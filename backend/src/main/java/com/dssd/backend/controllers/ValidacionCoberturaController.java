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

    public ValidacionCoberturaController(ValidacionCoberturaService service) {
        this.service = service;
    }

    @PostMapping
    public ValidacionCoberturaDTO validar(@PathVariable Long caseId,
            @Valid @RequestBody ValidacionCoberturaRequestDTO request) {
        return service.validarPorCaso(caseId, request);
    }

    @GetMapping
    public ValidacionCoberturaDTO consultar(@PathVariable Long caseId) {
        return service.consultarPorCaso(caseId);
    }
}
