<<<<<<< HEAD
package com.dssd.controller;

import com.dssd.dto.EmergenciaDto;
import com.dssd.model.Emergencia;
import com.dssd.repository.EmergenciaRepository;
import com.dssd.service.BonitaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/emergencias")
@CrossOrigin(origins = "*") // Permite peticiones desde el frontend Angular (puerto 4200)
public class EmergenciaController {

    @Autowired
    private EmergenciaRepository emergenciaRepository;

    @Autowired
    private BonitaService bonitaService;

    @PostMapping("/crear")
    public ResponseEntity<?> crearEmergencia(@RequestBody EmergenciaDto dto) {
        try {
            // 1. Guardar la emergencia localmente en PostgreSQL
            Emergencia emergencia = new Emergencia(
                    dto.getTitulo(),
                    dto.getDescripcion(),
                    dto.getNivelGravedad(),
                    dto.getZonaAfectada()
            );
            Emergencia emergenciaGuardada = emergenciaRepository.save(emergencia);

            // 2. Iniciar la instancia de la emergencia en Bonita y setear variables de proceso
            String respuestaBonita = bonitaService.iniciarInstanciaEmergencia(
                    emergenciaGuardada.getId(),
                    emergenciaGuardada.getNivelGravedad(),
                    emergenciaGuardada.getZonaAfectada()
            );

            // 3. Responder con el estado final
            Map<String, Object> response = new HashMap<>();
            response.put("mensaje", "Emergencia creada e instancia en Bonita iniciada con éxito");
            response.put("emergencia", emergenciaGuardada);
            response.put("bonitaResponse", respuestaBonita);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Error al procesar la emergencia: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}
=======
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
>>>>>>> origin/dev
