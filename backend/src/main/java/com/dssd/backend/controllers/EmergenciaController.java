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