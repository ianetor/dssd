package com.dssd.backend.controllers;

import com.dssd.backend.dtos.LoginRequestDTO;
import com.dssd.backend.dtos.UsuarioResponseDTO;
import com.dssd.backend.services.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<UsuarioResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        UsuarioResponseDTO usuario = authService.login(request);
        return ResponseEntity.ok(usuario);
    }
}
