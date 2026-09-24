package com.dssd.backend.services;

import com.dssd.backend.dtos.LoginRequestDTO;
import com.dssd.backend.dtos.UsuarioResponseDTO;
import com.dssd.backend.models.Municipio;
import com.dssd.backend.models.Ong;
import com.dssd.backend.repositories.MunicipioRepository;
import com.dssd.backend.repositories.OngRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final RestClient restClient;
    private final String bonitaUrl;
    private final MunicipioRepository municipioRepository;
    private final OngRepository ongRepository;
    private final ObjectMapper objectMapper;

    public AuthService(
            @Value("${bonita.url:http://localhost:8081/bonita}") String bonitaUrl,
            MunicipioRepository municipioRepository,
            OngRepository ongRepository) {
        this.bonitaUrl = bonitaUrl;
        this.restClient = RestClient.builder().baseUrl(bonitaUrl).build();
        this.municipioRepository = municipioRepository;
        this.ongRepository = ongRepository;
        this.objectMapper = new ObjectMapper();
    }

    public UsuarioResponseDTO login(LoginRequestDTO request) {
        // 1. Petición a Bonita /loginservice
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", request.getUsername());
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
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas en Bonita BPM");
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo conectar con el servidor Bonita en " + bonitaUrl + ": " + ex.getMessage());
        }

        List<String> setCookies = loginResponse.getHeaders().getOrEmpty("Set-Cookie");
        String jsessionId = extractCookie(setCookies, "JSESSIONID");
        String apiToken = extractCookie(setCookies, "X-Bonita-API-Token");

        if (jsessionId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Respuesta de login inválida de Bonita (sin sesión)");
        }

        // 2. Obtener información de la sesión actual (/API/system/session/unusedId)
        Long userId = null;
        String userName = request.getUsername();
        try {
            String sessionJson = restClient.get()
                    .uri("/API/system/session/unusedId")
                    .cookie("JSESSIONID", jsessionId)
                    .header("X-Bonita-API-Token", apiToken != null ? apiToken : "")
                    .retrieve()
                    .body(String.class);

            JsonNode sessionNode = objectMapper.readTree(sessionJson);
            if (sessionNode.has("user_id")) {
                userId = sessionNode.get("user_id").asLong();
            }
            if (sessionNode.has("user_name")) {
                userName = sessionNode.get("user_name").asText();
            }
        } catch (Exception e) {
            // Continuar con los datos disponibles
        }

        // 3. Obtener detalles de identidad del usuario (/API/identity/user/{userId})
        String nombreCompleto = userName;
        if (userId != null) {
            try {
                String userJson = restClient.get()
                        .uri("/API/identity/user/" + userId)
                        .cookie("JSESSIONID", jsessionId)
                        .header("X-Bonita-API-Token", apiToken != null ? apiToken : "")
                        .retrieve()
                        .body(String.class);

                JsonNode userNode = objectMapper.readTree(userJson);
                String firstName = userNode.hasNonNull("firstname") ? userNode.get("firstname").asText() : "";
                String lastName = userNode.hasNonNull("lastname") ? userNode.get("lastname").asText() : "";
                String full = (firstName + " " + lastName).trim();
                if (!full.isEmpty()) {
                    nombreCompleto = full;
                }
            } catch (Exception e) {
                // Si no responde el detalle, mantenemos userName
            }
        }

        // 4. Obtener membresías y roles (/API/identity/membership?f=user_id={userId}&d=role_id&d=group_id)
        String rolIdentificado = "";
        String rolDisplayName = "";

        if (userId != null) {
            try {
                String membershipJson = restClient.get()
                        .uri("/API/identity/membership?f=user_id=" + userId + "&d=role_id&d=group_id")
                        .cookie("JSESSIONID", jsessionId)
                        .header("X-Bonita-API-Token", apiToken != null ? apiToken : "")
                        .retrieve()
                        .body(String.class);

                JsonNode memberships = objectMapper.readTree(membershipJson);
                if (memberships.isArray() && !memberships.isEmpty()) {
                    for (JsonNode m : memberships) {
                        if (m.has("role_id")) {
                            JsonNode roleObj = m.get("role_id");
                            String roleName = roleObj.hasNonNull("name") ? roleObj.get("name").asText().toLowerCase() : "";
                            String roleDisplay = roleObj.hasNonNull("displayName") ? roleObj.get("displayName").asText() : "";

                            RolDeterminado rolDet = normalizarRol(roleName, roleDisplay);
                            rolIdentificado = rolDet.rolCodigo;
                            rolDisplayName = rolDet.rolNombre;
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                // Fallback por username si la membresía falla
            }
        }

        // 5. Vincular con entidades de base de datos local según el rol
        Long entidadId = null;
        String entidadNombre = null;

        if ("OPERADOR_MUNICIPAL".equals(rolIdentificado)) {
            Optional<Municipio> municipio = municipioRepository.findByBonitaUsername(userName);
            if (municipio.isPresent()) {
                entidadId = municipio.get().getId();
                entidadNombre = municipio.get().getNombre();
            } else {
                // Si no está registrado en la base local, tomar el primer municipio disponible o usar nombre por defecto
                List<Municipio> todos = municipioRepository.findAll();
                if (!todos.isEmpty()) {
                    entidadId = todos.get(0).getId();
                    entidadNombre = todos.get(0).getNombre();
                } else {
                    entidadNombre = "Municipio " + nombreCompleto;
                }
            }
        } else if ("REPRESENTANTE_ONG".equals(rolIdentificado)) {
            Optional<Ong> ong = ongRepository.findByBonitaUsername(userName);
            if (ong.isPresent()) {
                entidadId = ong.get().getId();
                entidadNombre = ong.get().getNombre();
            } else {
                List<Ong> todas = ongRepository.findAll();
                if (!todas.isEmpty()) {
                    entidadId = todas.get(0).getId();
                    entidadNombre = todas.get(0).getNombre();
                } else {
                    entidadNombre = "ONG " + nombreCompleto;
                }
            }
        }

        return UsuarioResponseDTO.builder()
                .id(userId != null ? userId : 1L)
                .username(userName)
                .nombreCompleto(nombreCompleto)
                .rol(rolIdentificado)
                .rolDisplayName(rolDisplayName)
                .entidadId(entidadId)
                .entidadNombre(entidadNombre)
                .build();
    }

    private record RolDeterminado(String rolCodigo, String rolNombre) {}

    private RolDeterminado normalizarRol(String name, String displayName) {
        String combined = (name + " " + displayName).toLowerCase();
        if (combined.contains("coord")) {
            return new RolDeterminado("COORDINADOR_REGIONAL", "Centro Coordinador Regional");
        }
        if (combined.contains("ong") || combined.contains("representante")) {
            return new RolDeterminado("REPRESENTANTE_ONG", "Representante de ONG");
        }
        if (combined.contains("audit") || combined.contains("directiv")) {
            return new RolDeterminado("AUDITOR_DIRECTIVO", "Auditor / Directivo");
        }
        return new RolDeterminado("OPERADOR_MUNICIPAL", "Operador Municipal");
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
