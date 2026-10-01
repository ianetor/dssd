package com.dssd.backend.services;

import com.dssd.backend.dtos.LoteRequestDTO;
import com.dssd.backend.dtos.LoteResponseDTO;
import com.dssd.backend.dtos.EmergenciasDTO.EmergenciaRequestDTO;
import com.dssd.backend.dtos.EmergenciasDTO.EmergenciaResponseDTO;
import com.dssd.backend.models.*;
import com.dssd.backend.repositories.EmergenciaRepository;
import com.dssd.backend.repositories.LoteNecesidadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@Transactional
public class EmergenciaService {

    private final EmergenciaRepository emergenciaRepository;
    private final LoteNecesidadRepository loteNecesidadRepository;
    private final BonitaService bonitaService;

    public EmergenciaService(EmergenciaRepository emergenciaRepository,
                             LoteNecesidadRepository loteNecesidadRepository,
                             BonitaService bonitaService)
    {
        this.emergenciaRepository = emergenciaRepository;
        this.loteNecesidadRepository = loteNecesidadRepository;
        this.bonitaService = bonitaService;
    }

    @Transactional
public EmergenciaResponseDTO crearEmergencia(EmergenciaRequestDTO dto) {
    // 1. Instanciar y setear datos del DTO
    Emergencia emergencia = dto.aEntidad();
    System.out.println("Emergencia antes de guardar: " );

    Emergencia guardada = emergenciaRepository.save(emergencia);


    try {
        Long caseId = bonitaService.iniciarInstanciaEmergencia(dto,guardada.getId());
        guardada.setCaseId(caseId);

    } catch (Exception e) {
        // Lanza excepción para hacer Rollback en la BD local si Bonita falla
        throw new RuntimeException("No se pudo iniciar el flujo de proceso en Bonita: " + e.getMessage(), e);
    }

        return toResponseDTO(guardada);
    }

    public EmergenciaResponseDTO publicarLotes(Long emergenciaId, List<LoteRequestDTO> lotesDto,
                                               Integer plazoRecepcionHoras) {
        Emergencia emergencia = emergenciaRepository.findById(emergenciaId)
                .orElseThrow(() -> new NoSuchElementException("Emergencia no encontrada con ID: " + emergenciaId));

        if (plazoRecepcionHoras == null || !List.of(4, 8, 12, 24).contains(plazoRecepcionHoras)) {
            throw new IllegalArgumentException("El plazo de recepción debe ser 4, 8, 12 o 24 horas");
        }

        emergencia.setEstado("CONVOCATORIA_ABIERTA");
        emergencia.setPlazoRecepcionHoras(plazoRecepcionHoras);
        emergencia.setFechaVencimiento(Instant.now().plusSeconds(plazoRecepcionHoras * 3600L));

        if (lotesDto != null && !lotesDto.isEmpty()) {
            List<LoteNecesidad> nuevosLotes = lotesDto.stream().map(loteDto -> {
                LoteNecesidad lote = new LoteNecesidad();
                lote.setTipoRecurso(loteDto.getTipoRecurso());
                lote.setCantidadRequerida(loteDto.getCantidadRequerida());
                lote.setCantidadCubierta(0);
                lote.setEmergencia(emergencia);
                return lote;
            }).collect(Collectors.toList());

            loteNecesidadRepository.saveAll(nuevosLotes);
            emergencia.getLotes().addAll(nuevosLotes);
        }

        Emergencia actualizada = emergenciaRepository.save(emergencia);

        // AVANZAR TAREA EN BONITA
        try {
            if (actualizada.getCaseId() != null) {
                bonitaService.avanzarPublicacionConvocatoria(actualizada.getCaseId(),
                        actualizada.getPlazoRecepcionHoras(), actualizada.getFechaVencimiento());
            }
        } catch (Exception e) {
            System.err.println("Error al avanzar tarea en Bonita: " + e.getMessage());
        }

        return toResponseDTO(actualizada);
    }

    @Transactional(readOnly = true)
    public List<EmergenciaResponseDTO> listarEmergencias(String estado) {
        List<Emergencia> emergencias;
        if (estado != null && !estado.isBlank()) {
            emergencias = emergenciaRepository.findByEstado(estado);
        } else {
            emergencias = emergenciaRepository.findAll();
        }

        return emergencias.stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public EmergenciaResponseDTO obtenerPorId(Long id) {
        Emergencia emergencia = emergenciaRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Emergencia no encontrada con ID: " + id));
        return toResponseDTO(emergencia);
    }

    @Transactional(readOnly = true)
    public List<LoteResponseDTO> obtenerLotesDeEmergencia(Long emergenciaId) {
        return loteNecesidadRepository.findByEmergenciaId(emergenciaId).stream()
                .map(this::toLoteResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obtenerTimer(Long emergenciaId) {
        Emergencia emergencia = emergenciaRepository.findById(emergenciaId)
                .orElseThrow(() -> new NoSuchElementException("Emergencia no encontrada con ID: " + emergenciaId));
        if (emergencia.getCaseId() == null) {
            throw new IllegalStateException("La emergencia todavía no tiene un caso de Bonita");
        }
        Map<String, Object> timer = bonitaService.obtenerTimerCaso(emergencia.getCaseId());
        if (timer.containsKey("plazoRecepcionHoras") && emergencia.getFechaVencimiento() != null) {
            timer.putIfAbsent("fechaVencimiento", emergencia.getFechaVencimiento().toString());
        }
        return timer;
    }



    public EmergenciaResponseDTO toResponseDTO(Emergencia emergencia) {
        EmergenciaResponseDTO.EmergenciaResponseDTOBuilder builder = EmergenciaResponseDTO.builder()
                .id(emergencia.getId())
                .caseId(emergencia.getCaseId())
                .plazoRecepcionHoras(emergencia.getPlazoRecepcionHoras())
                .fechaVencimiento(emergencia.getFechaVencimiento())
                .nivelGravedad(emergencia.getNivelGravedad())
                .zonaAfectada(emergencia.getZonaAfectada())
                .descripcion(emergencia.getDescripcion())
                .estado(emergencia.getEstado())
                .municipioNombre(emergencia.getMunicipioNombre());

        emergencia.popularCamposEspecificos(builder);

        List<LoteResponseDTO> lotesDTO = (emergencia.getLotes() != null)
                ? emergencia.getLotes().stream().map(this::toLoteResponseDTO).collect(Collectors.toList())
                : new ArrayList<>();

        builder.lotes(lotesDTO);

        return builder.build();
    }

    private LoteResponseDTO toLoteResponseDTO(LoteNecesidad lote) {
        return LoteResponseDTO.builder()
                .id(lote.getId())
                .tipoRecurso(lote.getTipoRecurso())
                .cantidadRequerida(lote.getCantidadRequerida())
                .cantidadCubierta(lote.getCantidadCubierta() != null ? lote.getCantidadCubierta() : 0)
                .build();
    }
}
