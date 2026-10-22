package com.dssd.backend.controllers;

import com.dssd.backend.dtos.LoginRequestDTO;
import com.dssd.backend.dtos.UsuarioResponseDTO;
import com.dssd.backend.services.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import com.dssd.backend.config.AccesoCoordinador;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<UsuarioResponseDTO> login(@Valid @RequestBody LoginRequestDTO request, HttpServletRequest http) {
        UsuarioResponseDTO usuario = authService.login(request);
        http.getSession(true);
        http.changeSessionId();
        http.getSession().setAttribute(AccesoCoordinador.USUARIO, usuario);
        return ResponseEntity.ok(usuario);
    }

    @GetMapping("/me")
    public UsuarioResponseDTO me(HttpServletRequest request) { return new AccesoCoordinador().usuario(request); }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        return ResponseEntity.noContent().build();
    }
}
