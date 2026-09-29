package com.dssd.backend.controllers;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dssd.backend.services.BonitaService;

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
}