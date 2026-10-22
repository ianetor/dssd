package com.dssd.backend.controllers;
import com.dssd.backend.config.AccesoIntegracion;
import com.dssd.backend.dtos.RevisionCoberturaDTO;
import com.dssd.backend.services.RevisionCoberturaService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/integracion")
public class IntegracionCoberturaController {
    private final AccesoIntegracion acceso;
    private final RevisionCoberturaService service;
    public IntegracionCoberturaController(AccesoIntegracion acceso, RevisionCoberturaService service) { this.acceso = acceso; this.service = service; }
    @PostMapping("/casos/{caseId}/rondas/{ronda}/depurar-ofertas")
    public RevisionCoberturaDTO.Snapshot depurar(@PathVariable Long caseId, @PathVariable int ronda,
            @RequestHeader(value="X-Integration-Key", required=false) String key) {
        acceso.exigir(key); return service.depurar(caseId, ronda);
    }
    @PostMapping("/decisiones/{id}/aplicar")
    public RevisionCoberturaService.Aplicacion aplicar(@PathVariable String id,
            @RequestHeader(value="X-Integration-Key", required=false) String key) {
        acceso.exigir(key); return service.aplicar(id);
    }
}
