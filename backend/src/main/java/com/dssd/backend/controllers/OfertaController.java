package com.dssd.backend.controllers;

import com.dssd.backend.dtos.OfertaRequestDTO;
import com.dssd.backend.dtos.OfertaResponseDTO;
import com.dssd.backend.dtos.UsuarioResponseDTO;
import com.dssd.backend.services.OfertaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Endpoints REST para la gestión de ofertas de ayuda.
 *
 * El frontend envía el username del representante ONG autenticado en el header
 * {@code X-ONG-Username}. No se utiliza Spring Security en este proyecto;
 * la autenticación real se delega a Bonita BPM y el frontend ya valida el rol.
 */
@RestController
@RequestMapping("/api/ofertas")
@RequiredArgsConstructor
public class OfertaController {

    private final OfertaService ofertaService;

    /** Construye un DTO mínimo de usuario a partir del header de autenticación. */
    private UsuarioResponseDTO usuarioDesdeHeader(String ongUsername) {
        if (ongUsername == null || ongUsername.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Debe enviar el header X-ONG-Username con su nombre de usuario.");
        }
        return UsuarioResponseDTO.builder()
                .username(ongUsername.trim())
                .rol("REPRESENTANTE_ONG")
                .build();
    }

    /**
     * Lista las ofertas del representante ONG autenticado para una emergencia.
     * GET /api/ofertas?emergenciaId={id}
     */
    @GetMapping
    public ResponseEntity<List<OfertaResponseDTO>> listar(
            @RequestHeader("X-ONG-Username") String ongUsername,
            @RequestParam(required = false) Long emergenciaId) {
        return ResponseEntity.ok(ofertaService.listar(usuarioDesdeHeader(ongUsername), emergenciaId));
    }

    /**
     * Registra una nueva oferta.
     * POST /api/ofertas
     */
    @PostMapping
    public ResponseEntity<OfertaResponseDTO> crear(
            @RequestHeader("X-ONG-Username") String ongUsername,
            @Valid @RequestBody OfertaRequestDTO dto) {
        OfertaResponseDTO creada = ofertaService.crear(usuarioDesdeHeader(ongUsername), dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    /**
     * Rectifica una oferta existente (solo el mismo representante puede modificarla).
     * PUT /api/ofertas/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<OfertaResponseDTO> actualizar(
            @RequestHeader("X-ONG-Username") String ongUsername,
            @PathVariable Long id,
            @Valid @RequestBody OfertaRequestDTO dto) {
        return ResponseEntity.ok(ofertaService.actualizar(usuarioDesdeHeader(ongUsername), id, dto));
    }

    /**
     * Retira (marca como Retirada) una oferta propia.
     * DELETE /api/ofertas/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> retirar(
            @RequestHeader("X-ONG-Username") String ongUsername,
            @PathVariable Long id) {
        ofertaService.retirar(usuarioDesdeHeader(ongUsername), id);
        return ResponseEntity.noContent().build();
    }
}
