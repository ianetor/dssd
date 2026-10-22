package com.dssd.backend.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class AccesoIntegracion {
    private final String clave;
    public AccesoIntegracion(@Value("${BONITA_INTEGRATION_KEY:}") String clave) { this.clave = clave; }
    public void exigir(String presentada) {
        if (clave.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Configure la clave de integración de Bonita");
        if (presentada == null || !MessageDigest.isEqual(clave.getBytes(StandardCharsets.UTF_8), presentada.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credencial de integración inválida");
        }
    }
}
