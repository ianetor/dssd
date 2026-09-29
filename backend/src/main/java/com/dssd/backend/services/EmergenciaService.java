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
import java.util.List;
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
                             BonitaService bonitaService) {
        this.emergenciaRepository = emergenciaRepository;
        this.loteNecesidadRepository = loteNecesidadRepository;
        this.bonitaService = bonitaService;
    }

    public EmergenciaResponseDTO crearEmergencia(EmergenciaRequestDTO dto) {
        Emergencia emergencia = instanciarPorTipo(dto);

        emergencia.setNivelGravedad(dto.getNivelGravedad());
        emergencia.setZonaAfectada(dto.getZonaAfectada());
        emergencia.setDescripcion(dto.getDescripcion());
        emergencia.setEstado("REGISTRADA");
        emergencia.setMunicipioNombre(dto.getMunicipioNombre());

        // 1. Guardar primero en BD para obtener el id de la Emergencia
        Emergencia guardada = emergenciaRepository.save(emergencia);

        // 2. Iniciar el proceso en Bonita y obtener el caseId
        try {
            Long caseId = bonitaService.iniciarInstanciaEmergencia(
                guardada.getId(),
                guardada.getNivelGravedad(),
                guardada.getZonaAfectada()
            );
            // 3. Setear el caseId en la entidad y volver a guardar
            guardada.setCaseId(caseId);
            guardada = emergenciaRepository.save(guardada);
        } catch (Exception e) {
            System.err.println("Error al iniciar instancia en Bonita: " + e.getMessage());
        }

        return toResponseDTO(guardada);
    }

    public EmergenciaResponseDTO publicarLotes(Long emergenciaId, List<LoteRequestDTO> lotesDto) {
        Emergencia emergencia = emergenciaRepository.findById(emergenciaId)
                .orElseThrow(() -> new NoSuchElementException("Emergencia no encontrada con ID: " + emergenciaId));

        emergencia.setEstado("CONVOCATORIA_ABIERTA");

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

    private Emergencia instanciarPorTipo(EmergenciaRequestDTO dto) {
        return dto.aEntidad();
    }

    public EmergenciaResponseDTO toResponseDTO(Emergencia emergencia) {
        EmergenciaResponseDTO.EmergenciaResponseDTOBuilder builder = EmergenciaResponseDTO.builder()
                .id(emergencia.getId())
                .caseId(emergencia.getCaseId())
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
