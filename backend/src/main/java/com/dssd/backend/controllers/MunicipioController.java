package com.dssd.backend.controllers;

import com.dssd.backend.dtos.MunicipioResponseDTO;
import com.dssd.backend.services.MunicipioService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/municipios")
public class MunicipioController {

    private final MunicipioService municipioService;

    public MunicipioController(MunicipioService municipioService) {
        this.municipioService = municipioService;
    }

    @GetMapping
    public ResponseEntity<List<MunicipioResponseDTO>> listarMunicipios() {
        return ResponseEntity.ok(municipioService.listarMunicipios());
    }
}
