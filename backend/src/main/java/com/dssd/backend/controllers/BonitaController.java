package com.dssd.backend.controllers;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dssd.backend.services.BonitaClientService;

@RestController
@RequestMapping("/api/bonita")
public class BonitaController {

    private final BonitaClientService bonitaClientService;

    public BonitaController(BonitaClientService bonitaClientService) {
        this.bonitaClientService = bonitaClientService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(bonitaClientService.healthStatus());
    }

    @GetMapping("/procesos")
    public ResponseEntity<String> procesos() {
        return ResponseEntity.ok(bonitaClientService.listProcesses());
    }

    @GetMapping("/tareas")
    public ResponseEntity<String> tareas() {
        return ResponseEntity.ok(bonitaClientService.listPendingTasks());
    }

    @GetMapping("/casos")
    public ResponseEntity<String> casos() {
        return ResponseEntity.ok(bonitaClientService.listCases());
    }

    @GetMapping("/usuarios")
    public ResponseEntity<String> usuarios() {
        return ResponseEntity.ok(bonitaClientService.listUsers());
    }

    @GetMapping("/recurso/{recurso}")
    public ResponseEntity<String> recurso(
            @PathVariable String recurso,
            @RequestParam Map<String, String> queryParams) {
        return ResponseEntity.ok(bonitaClientService.getResource(recurso, queryParams));
    }
}