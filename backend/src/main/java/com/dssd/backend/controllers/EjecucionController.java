package com.dssd.backend.controllers;

import com.dssd.backend.config.AccesoCoordinador;
import com.dssd.backend.config.AccesoIntegracion;
import com.dssd.backend.dtos.EjecucionDTO;
import com.dssd.backend.dtos.UsuarioResponseDTO;
import com.dssd.backend.services.EjecucionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Etapa de ejecución. Tres públicos, cada uno con el mecanismo de acceso que ya usa el proyecto:
 * Bonita (X-Integration-Key), Centro Coordinador (sesión) y ONG (X-ONG-Username).
 */
@RestController
@RequestMapping("/api")
public class EjecucionController {
    private final EjecucionService service;
    private final AccesoIntegracion integracion;
    private final AccesoCoordinador coordinador;

    public EjecucionController(EjecucionService service, AccesoIntegracion integracion, AccesoCoordinador coordinador) {
        this.service = service;
        this.integracion = integracion;
        this.coordinador = coordinador;
    }

    // --- Bonita ---

    /** Conector de "Notificar a ONGs adjudicadas". */
    @PostMapping("/integracion/casos/{caseId}/ejecucion/iniciar")
    public EjecucionDTO.Estado iniciar(@PathVariable Long caseId,
            @RequestHeader(value = "X-Integration-Key", required = false) String key) {
        integracion.exigir(key);
        return service.iniciar(caseId);
    }

    /** Conector de "Cierre y liberacion de recursos", después de liberar en el Sistema Nacional. */
    @PostMapping("/integracion/casos/{caseId}/ejecucion/cerrar")
    public EjecucionDTO.Estado cerrar(@PathVariable Long caseId,
            @RequestHeader(value = "X-Integration-Key", required = false) String key) {
        integracion.exigir(key);
        return service.cerrar(caseId);
    }

    // --- Centro Coordinador (el auditor solo consulta) ---

    @GetMapping("/coordinador/emergencias/{id}/ejecucion")
    public EjecucionDTO.Estado monitorear(@PathVariable Long id, HttpServletRequest request) {
        UsuarioResponseDTO usuario = coordinador.usuario(request);
        if (!List.of("COORDINADOR_REGIONAL", "AUDITOR_DIRECTIVO").contains(usuario.getRol())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene acceso al monitoreo");
        }
        return service.consultar(id);
    }

    @PostMapping("/coordinador/emergencias/{id}/ejecucion/cierre")
    public EjecucionDTO.Estado solicitarCierre(@PathVariable Long id,
            @Valid @RequestBody(required = false) EjecucionDTO.CierreRequest body, HttpServletRequest request) {
        return service.solicitarCierre(id, body, coordinador.exigir(request));
    }

    // --- ONG ---

    @GetMapping("/ejecucion/adjudicaciones")
    public List<EjecucionDTO.Adjudicacion> adjudicaciones(@RequestHeader("X-ONG-Username") String ong) {
        return service.adjudicacionesDe(ong(ong));
    }

    @PostMapping("/ejecucion/emergencias/{id}/finalizacion")
    public EjecucionDTO.Participacion finalizar(@PathVariable Long id, @RequestHeader("X-ONG-Username") String ong,
            @Valid @RequestBody(required = false) EjecucionDTO.FinalizacionRequest body) {
        return service.finalizar(ong(ong), id, body == null ? null : body.observaciones());
    }

    private static String ong(String username) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicie sesión nuevamente");
        }
        return username.trim();
    }
}
