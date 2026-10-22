package com.dssd.backend.controllers;
import com.dssd.backend.config.AccesoCoordinador;
import com.dssd.backend.dtos.*;
import com.dssd.backend.services.RevisionCoberturaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/coordinador")
public class CoordinadorCoberturaController {
    private final AccesoCoordinador acceso;
    private final RevisionCoberturaService service;
    public CoordinadorCoberturaController(AccesoCoordinador acceso, RevisionCoberturaService service) { this.acceso = acceso; this.service = service; }
    @GetMapping("/cobertura-insuficiente")
    public RevisionCoberturaService.Bandeja listar(@RequestParam(defaultValue="0") int pagina,
            @RequestParam(defaultValue="20") int cantidad, HttpServletRequest request) {
        acceso.exigir(request); return service.listar(pagina, cantidad);
    }
    @GetMapping("/emergencias/{id}/cobertura-insuficiente")
    public RevisionCoberturaDTO detalle(@PathVariable Long id, HttpServletRequest request) {
        acceso.exigir(request); return service.detalle(id);
    }
    @PostMapping("/emergencias/{id}/decisiones-cobertura")
    public ResponseEntity<RevisionCoberturaDTO.Decision> decidir(@PathVariable Long id,
            @Valid @RequestBody DecisionCoberturaRequestDTO body, HttpServletRequest request) {
        return ResponseEntity.accepted().body(service.decidir(id, body, acceso.exigir(request)));
    }
}
