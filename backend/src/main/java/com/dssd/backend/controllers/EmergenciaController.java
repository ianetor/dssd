package com.dssd.backend.controllers;

import com.dssd.backend.dtos.EmergenciaRequestDTO;
import com.dssd.backend.dtos.EmergenciaResponseDTO;
import com.dssd.backend.dtos.LoteRequestDTO;
import com.dssd.backend.dtos.LoteResponseDTO;
import com.dssd.backend.services.EmergenciaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/emergencias")
public class EmergenciaController {

    private final EmergenciaService emergenciaService;

    public EmergenciaController(EmergenciaService emergenciaService) {
        this.emergenciaService = emergenciaService;
    }

    @PostMapping
    public ResponseEntity<EmergenciaResponseDTO> registrarEmergencia(@RequestBody EmergenciaRequestDTO requestDTO) {
        EmergenciaResponseDTO creada = emergenciaService.crearEmergencia(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }


    @PostMapping("/{id}/lotes")
    public ResponseEntity<EmergenciaResponseDTO> publicarLotes(
            @PathVariable Long id,
            @RequestBody List<LoteRequestDTO> lotesDTO) {
        EmergenciaResponseDTO actualizada = emergenciaService.publicarLotes(id, lotesDTO);
        return ResponseEntity.ok(actualizada);
    }

    @GetMapping
    public ResponseEntity<List<EmergenciaResponseDTO>> listarEmergencias(
            @RequestParam(required = false) String estado) {
        return ResponseEntity.ok(emergenciaService.listarEmergencias(estado));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmergenciaResponseDTO> obtenerEmergenciaPorId(@PathVariable Long id) {
        return ResponseEntity.ok(emergenciaService.obtenerPorId(id));
    }

    @GetMapping("/{id}/lotes")
    public ResponseEntity<List<LoteResponseDTO>> obtenerLotesDeEmergencia(@PathVariable Long id) {
        return ResponseEntity.ok(emergenciaService.obtenerLotesDeEmergencia(id));
    }
}
