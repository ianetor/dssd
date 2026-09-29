package com.dssd.backend.controllers;

import java.net.http.HttpHeaders;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dssd.backend.dtos.EmergenciasDTO.EmergenciaRequestDTO;
import com.dssd.backend.services.BonitaService;

import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/bonita")
public class BonitaController {

    private final BonitaService bonitaService;

    public BonitaController(BonitaService bonitaService) {
        this.bonitaService = bonitaService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(bonitaService.healthStatus());
    }

    @GetMapping("/procesos")
    public ResponseEntity<String> procesos() {
        return ResponseEntity.ok(bonitaService.listProcesses());
    }

    @GetMapping("/tareas")
    public ResponseEntity<String> tareas() {
        return ResponseEntity.ok(bonitaService.listPendingTasks());
    }

    @GetMapping("/usuarios")
    public ResponseEntity<String> usuarios() {
        return ResponseEntity.ok(bonitaService.listUsers());
    }

    @GetMapping("/process-id")
    public ResponseEntity<Map<String, String>> testGetProcessId(
        @RequestParam(defaultValue = "RescueSync") String name,
        @RequestParam(defaultValue = "1.0") String version) throws Exception {

    org.springframework.http.HttpHeaders headers = bonitaService.login();
    String processId = bonitaService.getProcessDefinitionId(name, version, headers);

    return ResponseEntity.ok(Map.of(
        "processName", name,
        "version", version,
        "processDefinitionId", processId
        ));
    }
    @PostMapping("/instanciar")
    public ResponseEntity<Map<String,Object>> instanciarEmergencia(@RequestBody EmergenciaRequestDTO dto) {
    // Le pasamos un ID ficticio para prueba
        Long caseId = bonitaService.iniciarInstanciaEmergencia(dto, 1L);

        return ResponseEntity.ok(Map.of(
            "mensaje", "Proceso instanciado exitosamente en Bonita",
            "caseId", caseId
        ));
    }
}