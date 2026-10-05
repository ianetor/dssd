package com.dssd.backend.services;

import com.dssd.backend.dtos.PublicacionConvocatoriaRequestDTO;
import com.dssd.backend.dtos.LoteResponseDTO;
import com.dssd.backend.dtos.EmergenciasDTO.EmergenciaRequestDTO;
import com.dssd.backend.dtos.EmergenciasDTO.EmergenciaResponseDTO;
import com.dssd.backend.models.*;
import com.dssd.backend.repositories.EmergenciaRepository;
import com.dssd.backend.repositories.LoteNecesidadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.Instant;

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

    public EmergenciaResponseDTO publicarLotes(Long emergenciaId, PublicacionConvocatoriaRequestDTO solicitud) {
        Emergencia emergencia = emergenciaRepository.bloquearPorId(emergenciaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Emergencia no encontrada"));
        if (emergencia.getDuracionConvocatoriaMinutos() != null) {
            var existentes = emergencia.getLotes().stream()
                    .map(l -> new LoteComparable(l.getTipoRecurso(), l.getCantidadRequerida()))
                    .collect(Collectors.groupingBy(l -> l, Collectors.counting()));
            var solicitados = solicitud.lotes().stream()
                    .map(l -> new LoteComparable(l.getTipoRecurso().trim(), l.getCantidadRequerida()))
                    .collect(Collectors.groupingBy(l -> l, Collectors.counting()));
            if (!emergencia.getDuracionConvocatoriaMinutos().equals(solicitud.duracionMinutos())
                    || !existentes.equals(solicitados)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "La convocatoria ya tiene otra publicación registrada");
            }
            return toResponseDTO(emergencia);
        }
        if (!"REGISTRADA".equals(emergencia.getEstado()) || !emergencia.getLotes().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La emergencia no admite una nueva publicación");
        }
        if (emergencia.getCaseId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La emergencia no tiene un caso de Bonita asociado");
        }
        emergencia.setEstado("PUBLICACION_PENDIENTE");
        emergencia.setDuracionConvocatoriaMinutos(solicitud.duracionMinutos());
        emergencia.setPublicacionIntentos(0);
        emergencia.setPublicacionProximoIntento(Instant.now());
        List<LoteNecesidad> nuevosLotes = solicitud.lotes().stream().map(loteDto -> {
                LoteNecesidad lote = new LoteNecesidad();
                lote.setTipoRecurso(loteDto.getTipoRecurso().trim());
                lote.setCantidadRequerida(loteDto.getCantidadRequerida());
                lote.setCantidadCubierta(0);
                lote.setEmergencia(emergencia);
                return lote;
        }).collect(Collectors.toList());
        loteNecesidadRepository.saveAll(nuevosLotes);
        emergencia.getLotes().addAll(nuevosLotes);

        Emergencia actualizada = emergenciaRepository.save(emergencia);

        return toResponseDTO(actualizada);
    }

    private record LoteComparable(String tipo, Integer cantidad) {}

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



    public EmergenciaResponseDTO toResponseDTO(Emergencia emergencia) {
        EmergenciaResponseDTO.EmergenciaResponseDTOBuilder builder = EmergenciaResponseDTO.builder()
                .id(emergencia.getId())
                .caseId(emergencia.getCaseId())
                .nivelGravedad(emergencia.getNivelGravedad())
                .zonaAfectada(emergencia.getZonaAfectada())
                .descripcion(emergencia.getDescripcion())
                .estado(emergencia.getEstado())
                .duracionConvocatoriaMinutos(emergencia.getDuracionConvocatoriaMinutos())
                .fechaAperturaConvocatoria(emergencia.getFechaAperturaConvocatoria())
                .fechaVencimientoConvocatoria(emergencia.getFechaVencimientoConvocatoria())
                .fechaCierreConvocatoria(emergencia.getFechaCierreConvocatoria())
                .motivoCierre(emergencia.getMotivoCierre())
                .horaServidor(Instant.now())
                .publicacionIntentos(emergencia.getPublicacionIntentos())
                .publicacionError(emergencia.getPublicacionError())
                .avanceCoberturaEstado(emergencia.getAvanceCoberturaEstado())
                .avanceCoberturaError(emergencia.getAvanceCoberturaError())
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
