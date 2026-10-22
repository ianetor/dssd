package com.dssd.backend.config;

import com.dssd.backend.dtos.UsuarioResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AccesoCoordinador {
    public static final String USUARIO = "usuarioAutenticado";
    public UsuarioResponseDTO usuario(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null || !(session.getAttribute(USUARIO) instanceof UsuarioResponseDTO usuario)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicie sesión nuevamente");
        }
        return usuario;
    }
    public UsuarioResponseDTO exigir(HttpServletRequest request) {
        UsuarioResponseDTO usuario = usuario(request);
        if (!"COORDINADOR_REGIONAL".equals(usuario.getRol())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el Centro Coordinador puede acceder");
        }
        return usuario;
    }
}
