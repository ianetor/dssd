package com.dssd.backend.services;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Consulta de solo lectura al Sistema Nacional (catálogo y disponibilidad global).
 * La validación, el bloqueo y la liberación los sigue invocando Bonita; acá solo se lee
 * el catálogo para que el Centro Coordinador arme lotes con nombres que el Sistema Nacional reconoce.
 */
@Service
public class SistemaNacionalClient {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SistemaNacionalClient.class);
    /** Se renueva el token un minuto antes de su vencimiento para no usarlo al límite. */
    private static final long MARGEN_SEGUNDOS = 60;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RecursoNacional(String tipoRecurso, long cantidadTotal, long cantidadBloqueada,
            long cantidadDisponible, long ongsAportantes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Login(String accessToken, Long expiresInSeconds) {}

    private final RestClient http;
    private final String url;
    private final String username;
    private final String password;
    private String token;
    private Instant tokenVence = Instant.EPOCH;

    public SistemaNacionalClient(RestClient.Builder builder,
            @Value("${NACIONAL_URL:}") String url,
            @Value("${NACIONAL_USERNAME:}") String username,
            @Value("${NACIONAL_PASSWORD:}") String password) {
        this.url = url.trim().replaceAll("/+$", "");
        this.username = username.trim();
        this.password = password;
        this.http = builder.build();
    }

    public boolean configurado() {
        return !url.isBlank() && !username.isBlank() && !password.isBlank();
    }

    /** Tipos de recurso existentes en el país con su disponibilidad, sumando las ONGs habilitadas. */
    public List<RecursoNacional> disponibilidad() {
        if (!configurado()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El acceso al Sistema Nacional no está configurado");
        }
        try {
            try {
                return pedirDisponibilidad(tokenVigente());
            } catch (HttpClientErrorException.Unauthorized ex) {
                // El Sistema Nacional pudo reiniciarse o cambiar su secreto: se renueva el token una vez.
                return pedirDisponibilidad(renovarToken());
            }
        } catch (RestClientException | IllegalStateException ex) {
            log.warn("Catálogo nacional no disponible: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo consultar el catálogo del Sistema Nacional");
        }
    }

    private List<RecursoNacional> pedirDisponibilidad(String bearer) {
        List<RecursoNacional> recursos = http.get().uri(url + "/api/recursos/disponibilidad")
                .headers(h -> h.setBearerAuth(bearer))
                .retrieve().body(new ParameterizedTypeReference<List<RecursoNacional>>() {});
        return recursos == null ? List.of() : recursos;
    }

    private synchronized String tokenVigente() {
        return token != null && Instant.now().isBefore(tokenVence) ? token : renovarToken();
    }

    private synchronized String renovarToken() {
        Login login = http.post().uri(url + "/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "password", password))
                .retrieve().body(Login.class);
        if (login == null || login.accessToken() == null || login.accessToken().isBlank()) {
            throw new IllegalStateException("El login nacional no devolvió un token");
        }
        long vida = login.expiresInSeconds() == null ? 0 : login.expiresInSeconds();
        token = login.accessToken();
        tokenVence = Instant.now().plusSeconds(Math.max(0, vida - MARGEN_SEGUNDOS));
        return token;
    }
}
