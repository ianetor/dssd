package com.dssd.backend.controllers;

import com.dssd.backend.dtos.EmergenciasDTO.EmergenciaResponseDTO;
import com.dssd.backend.dtos.EmergenciasDTO.EmergenciaRequestDTO;
import com.dssd.backend.dtos.LoteRequestDTO;
import com.dssd.backend.dtos.LoteResponseDTO;
import com.dssd.backend.services.EmergenciaService;



import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.dssd.backend.services.BonitaService;
import java.util.List;

@RestController
@RequestMapping("/api/emergencias")
public class EmergenciaController {

    private final EmergenciaService emergenciaService;


    public EmergenciaController(EmergenciaService emergenciaService, BonitaService bonitaService) {
        this.emergenciaService = emergenciaService;
    }

    @PostMapping
    @CrossOrigin(origins = "http://localhost:4200", allowCredentials = "true")
    public ResponseEntity<EmergenciaResponseDTO> registrarEmergencia(@RequestBody EmergenciaRequestDTO requestDTO) {
        System.out.println("DTO Recibido: " + requestDTO.toString()); 
        System.out.println("Tipo de Emergencia: " + requestDTO.getTipoEmergencia());
        EmergenciaResponseDTO creada = emergenciaService.crearEmergencia(requestDTO);

        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @PostMapping("/{id}/lotes")
    public ResponseEntity<EmergenciaResponseDTO> publicarLotes(
            @PathVariable Long id,
            @jakarta.validation.Valid @RequestBody com.dssd.backend.dtos.PublicacionConvocatoriaRequestDTO solicitud) {
        EmergenciaResponseDTO actualizada = emergenciaService.publicarLotes(id, solicitud);
        return ResponseEntity.status("PUBLICACION_PENDIENTE".equals(actualizada.getEstado())
                ? HttpStatus.ACCEPTED : HttpStatus.OK).body(actualizada);
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
