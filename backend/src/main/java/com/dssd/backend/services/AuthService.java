package com.dssd.backend.services;

import com.dssd.backend.dtos.LoginRequestDTO;
import com.dssd.backend.dtos.UsuarioResponseDTO;
import com.dssd.backend.models.Municipio;
import com.dssd.backend.models.Ong;
import com.dssd.backend.repositories.MunicipioRepository;
import com.dssd.backend.repositories.OngRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final RestClient restClient;
    private final String bonitaUrl;
    private final MunicipioRepository municipioRepository;
    private final OngRepository ongRepository;
    private final ObjectMapper objectMapper;

    public AuthService(
            @Value("${bonita.url:${BONITA_URL:http://localhost:8081/bonita}}") String bonitaUrl,
            MunicipioRepository municipioRepository,
            OngRepository ongRepository) {
        this.bonitaUrl = bonitaUrl;
        this.restClient = RestClient.builder().baseUrl(bonitaUrl).build();
        this.municipioRepository = municipioRepository;
        this.ongRepository = ongRepository;
        this.objectMapper = new ObjectMapper();
    }

    public UsuarioResponseDTO login(LoginRequestDTO request) {
        String username = request.getUsername().trim();

        // 1. Autenticar credenciales en Bonita
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", username);
        form.add("password", request.getPassword());
        form.add("redirect", "false");

        ResponseEntity<Void> loginResponse;
        try {
            loginResponse = restClient.post()
                    .uri("/loginservice?redirect=false")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden ex) {
            log.warn("Credenciales inválidas en Bonita para el usuario: {}", username);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas en Bonita BPM");
        } catch (Exception ex) {
            log.error("Error al conectar con Bonita en {}: {}", bonitaUrl, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo conectar con Bonita BPM en " + bonitaUrl + ": " + ex.getMessage());
        }

        List<String> setCookies = loginResponse.getHeaders().getOrEmpty("Set-Cookie");
        String jsessionId = extractCookie(setCookies, "JSESSIONID");
        String apiToken = extractCookie(setCookies, "X-Bonita-API-Token");

        if (jsessionId == null) {
            log.error("Respuesta exitosa de Bonita pero sin cookie JSESSIONID");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Respuesta de login inválida de Bonita");
        }

        // 2. Obtener sesión de Bonita para conocer el user_id
        Long userId = null;
        try {
            String sessionJson = restClient.get()
                    .uri("/API/system/session/unusedId")
                    .cookie("JSESSIONID", jsessionId)
                    .header("X-Bonita-API-Token", apiToken != null ? apiToken : "")
                    .retrieve()
                    .body(String.class);

            JsonNode sessionNode = objectMapper.readTree(sessionJson);
            if (sessionNode.hasNonNull("user_id")) {
                userId = sessionNode.get("user_id").asLong();
            }
            log.info("Sesión activa en Bonita para usuario '{}', userId={}", username, userId);
        } catch (Exception e) {
            log.warn("No se pudo obtener el user_id desde /API/system/session/unusedId: {}", e.getMessage());
        }

        // 3. Obtener nombre y apellido del usuario desde Bonita
        String nombreCompleto = username;
        if (userId != null) {
            try {
                String userJson = restClient.get()
                        .uri("/API/identity/user/" + userId)
                        .cookie("JSESSIONID", jsessionId)
                        .header("X-Bonita-API-Token", apiToken != null ? apiToken : "")
                        .retrieve()
                        .body(String.class);

                JsonNode userNode = objectMapper.readTree(userJson);
                String firstName = userNode.hasNonNull("firstname") ? userNode.get("firstname").asText().trim() : "";
                String lastName = userNode.hasNonNull("lastname") ? userNode.get("lastname").asText().trim() : "";
                String full = (firstName + " " + lastName).trim();
                if (!full.isEmpty()) {
                    nombreCompleto = full;
                }
            } catch (Exception e) {
                log.warn("No se pudo obtener datos de usuario desde /API/identity/user/{}: {}", userId, e.getMessage());
            }
        }

        // 4. Obtener membresías y roles configurados en Bonita (con paginación obligatoria p=0&c=10)
        String rolIdentificado = null;
        String rolDisplayName = null;

        if (userId != null) {
            try {
                String membershipJson = restClient.get()
                        .uri("/API/identity/membership?p=0&c=10&f=user_id=" + userId + "&d=role_id&d=group_id")
                        .cookie("JSESSIONID", jsessionId)
                        .header("X-Bonita-API-Token", apiToken != null ? apiToken : "")
                        .retrieve()
                        .body(String.class);

                log.info("Membresías devueltas por Bonita para userId {}: {}", userId, membershipJson);
                JsonNode memberships = objectMapper.readTree(membershipJson);

                if (memberships.isArray()) {
                    for (JsonNode m : memberships) {
                        String roleName = "";
                        String roleDisplayNameFromBonita = "";

                        // A) Inspeccionar role_id
                        if (m.has("role_id")) {
                            JsonNode roleNode = m.get("role_id");
                            if (roleNode.isObject()) {
                                roleName = roleNode.hasNonNull("name") ? roleNode.get("name").asText() : "";
                                roleDisplayNameFromBonita = roleNode.hasNonNull("displayName") ? roleNode.get("displayName").asText() : "";
                            } else if (roleNode.isTextual() || roleNode.isNumber()) {
                                // Si viene el ID como número o string plano, consultamos el rol a Bonita
                                String roleId = roleNode.asText();
                                try {
                                    String roleDetailJson = restClient.get()
                                            .uri("/API/identity/role/" + roleId)
                                            .cookie("JSESSIONID", jsessionId)
                                            .header("X-Bonita-API-Token", apiToken != null ? apiToken : "")
                                            .retrieve()
                                            .body(String.class);

                                    JsonNode roleDetail = objectMapper.readTree(roleDetailJson);
                                    roleName = roleDetail.hasNonNull("name") ? roleDetail.get("name").asText() : "";
                                    roleDisplayNameFromBonita = roleDetail.hasNonNull("displayName") ? roleDetail.get("displayName").asText() : "";
                                } catch (Exception exRole) {
                                    log.warn("No se pudo obtener detalle del rol {} de Bonita: {}", roleId, exRole.getMessage());
                                }
                            }
                        }

                        // B) Inspeccionar group_id
                        String groupName = "";
                        String groupDisplayNameFromBonita = "";
                        if (m.has("group_id")) {
                            JsonNode groupNode = m.get("group_id");
                            if (groupNode.isObject()) {
                                groupName = groupNode.hasNonNull("name") ? groupNode.get("name").asText() : "";
                                groupDisplayNameFromBonita = groupNode.hasNonNull("displayName") ? groupNode.get("displayName").asText() : "";
                            }
                        }

                        log.info("Evaluando membresía: roleName='{}', roleDisplayName='{}', groupName='{}'",
                                roleName, roleDisplayNameFromBonita, groupName);

                        RolDeterminado rolDeterminado = clasificarRolDesdeBonita(
                                roleName, roleDisplayNameFromBonita, groupName, groupDisplayNameFromBonita);

                        if (rolDeterminado != null) {
                            rolIdentificado = rolDeterminado.codigo;
                            rolDisplayName = rolDeterminado.nombre;
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error al consultar membresías de Bonita para userId {}: {}", userId, e.getMessage());
            }
        }

        // Si Bonita no devolvió un rol reconocido, asignar OPERADOR_MUNICIPAL por defecto
        if (rolIdentificado == null) {
            log.warn("No se pudo identificar el rol desde las membresías de Bonita para el usuario '{}'.", username);
            rolIdentificado = "SIN ROL";
            rolDisplayName = "Sin Rol";
        }

        log.info("Rol final resuelto para el usuario '{}': {} ({})", username, rolIdentificado, rolDisplayName);

        // 5. Vincular entidad local (Municipio u ONG) según el rol determinado por Bonita
        Long entidadId = null;
        String entidadNombre = null;

        return UsuarioResponseDTO.builder()
                .id(userId != null ? userId : 1L)
                .username(username)
                .nombreCompleto(nombreCompleto)
                .rol(rolIdentificado)
                .rolDisplayName(rolDisplayName)
                .entidadId(entidadId)
                .entidadNombre(entidadNombre)
                .build();
    }

    private record RolDeterminado(String codigo, String nombre) {}

    private RolDeterminado clasificarRolDesdeBonita(String roleName, String roleDisplayName, String groupName, String groupDisplayName) {
        String texto = (roleName + " " + roleDisplayName + " " + groupName + " " + groupDisplayName).toLowerCase();

        // 1. Centro Coordinador Regional
        if (texto.contains("coord") || texto.contains("regional")) {
            return new RolDeterminado("COORDINADOR_REGIONAL", "Centro Coordinador Regional");
        }

        // 2. Representante de ONG
        if (texto.contains("ong") || texto.contains("representante") || texto.contains("rescate")) {
            return new RolDeterminado("REPRESENTANTE_ONG", "Representante de ONG");
        }

        // 3. Auditor / Directivo
        if (texto.contains("audit") || texto.contains("directiv")) {
            return new RolDeterminado("AUDITOR_DIRECTIVO", "Auditor / Directivo");
        }

        // 4. Operador Municipal
        if (texto.contains("muni") || texto.contains("opera") || texto.contains("local")) {
            return new RolDeterminado("OPERADOR_MUNICIPAL", "Operador Municipal");
        }

        return null;
    }

    private String extractCookie(List<String> cookies, String cookieName) {
        if (cookies == null) return null;
        for (String cookie : cookies) {
            for (String part : cookie.split(";")) {
                String trimmed = part.trim();
                if (trimmed.startsWith(cookieName + "=")) {
                    return trimmed.substring((cookieName + "=").length());
                }
            }
        }
        return null;
    }
}
