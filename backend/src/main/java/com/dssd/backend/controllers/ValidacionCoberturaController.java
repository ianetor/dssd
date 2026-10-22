package com.dssd.backend.controllers;

import com.dssd.backend.dtos.ValidacionCoberturaDTO;
import com.dssd.backend.dtos.ValidacionCoberturaRequestDTO;
import com.dssd.backend.services.ValidacionCoberturaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/emergencias/caso/{caseId}/validacion-cobertura")
public class ValidacionCoberturaController {
    private final ValidacionCoberturaService service;
    private final com.dssd.backend.config.AccesoIntegracion acceso;

    public ValidacionCoberturaController(ValidacionCoberturaService service, com.dssd.backend.config.AccesoIntegracion acceso) {
        this.service = service;
        this.acceso = acceso;
    }

    @PostMapping
    public ValidacionCoberturaDTO validar(@PathVariable Long caseId,
            @Valid @RequestBody ValidacionCoberturaRequestDTO request,
            @RequestHeader(value="X-Integration-Key", required=false) String key) {
        acceso.exigir(key);
        return service.validarPorCaso(caseId, request);
    }

    @GetMapping
    public ValidacionCoberturaDTO consultar(@PathVariable Long caseId) {
        return service.consultarPorCaso(caseId);
    }
}
