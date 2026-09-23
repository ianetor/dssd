package com.dssd.backend.services;

import com.dssd.backend.dtos.EmergenciaRequestDTO;
import com.dssd.backend.dtos.EmergenciaResponseDTO;
import com.dssd.backend.dtos.LoteRequestDTO;
import com.dssd.backend.dtos.LoteResponseDTO;
import com.dssd.backend.models.*;
import com.dssd.backend.repositories.EmergenciaRepository;
import com.dssd.backend.repositories.LoteNecesidadRepository;
import com.dssd.backend.repositories.MunicipioRepository;
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
    private final MunicipioRepository municipioRepository;
    private final LoteNecesidadRepository loteNecesidadRepository;

    public EmergenciaService(EmergenciaRepository emergenciaRepository,
                             MunicipioRepository municipioRepository,
                             LoteNecesidadRepository loteNecesidadRepository) {
        this.emergenciaRepository = emergenciaRepository;
        this.municipioRepository = municipioRepository;
        this.loteNecesidadRepository = loteNecesidadRepository;
    }

    public EmergenciaResponseDTO crearEmergencia(EmergenciaRequestDTO dto) {
        Emergencia emergencia = instanciarPorTipo(dto);

        emergencia.setNivelGravedad(dto.getNivelGravedad());
        emergencia.setZonaAfectada(dto.getZonaAfectada());
        emergencia.setDescripcion(dto.getDescripcion());
        emergencia.setEstado("REGISTRADA");

        if (dto.getMunicipioId() != null) {
            municipioRepository.findById(dto.getMunicipioId())
                    .ifPresent(emergencia::setMunicipioAfectado);
        }

        Emergencia guardada = emergenciaRepository.save(emergencia);
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
        String tipo = dto.getTipoEmergencia() != null ? dto.getTipoEmergencia().trim().toUpperCase() : "INCENDIO";

        if (tipo.contains("INUNDA")) {
            Inundacion inundacion = new Inundacion();
            inundacion.setMilimetrosAgua(dto.getMilimetrosAgua());
            return inundacion;
        } else if (tipo.contains("TERRE")) {
            Terremoto terremoto = new Terremoto();
            terremoto.setMagnitudRichter(dto.getMagnitudRichter());
            return terremoto;
        } else {
            Incendio incendio = new Incendio();
            incendio.setHectareasAfectadas(dto.getHectareasAfectadas());
            return incendio;
        }
    }

    public EmergenciaResponseDTO toResponseDTO(Emergencia emergencia) {
        EmergenciaResponseDTO.EmergenciaResponseDTOBuilder builder = EmergenciaResponseDTO.builder()
                .id(emergencia.getId())
                .nivelGravedad(emergencia.getNivelGravedad())
                .zonaAfectada(emergencia.getZonaAfectada())
                .descripcion(emergencia.getDescripcion())
                .estado(emergencia.getEstado());

        if (emergencia.getMunicipioAfectado() != null) {
            builder.municipioId(emergencia.getMunicipioAfectado().getId())
                   .municipioNombre(emergencia.getMunicipioAfectado().getNombre());
        }

        if (emergencia instanceof Incendio inc) {
            builder.tipoEmergencia("INCENDIO")
                   .hectareasAfectadas(inc.getHectareasAfectadas());
        } else if (emergencia instanceof Inundacion inu) {
            builder.tipoEmergencia("INUNDACION")
                   .milimetrosAgua(inu.getMilimetrosAgua());
        } else if (emergencia instanceof Terremoto ter) {
            builder.tipoEmergencia("TERREMOTO")
                   .magnitudRichter(ter.getMagnitudRichter());
        }

        if (emergencia.getLotes() != null) {
            List<LoteResponseDTO> lotesDTO = emergencia.getLotes().stream()
                    .map(this::toLoteResponseDTO)
                    .collect(Collectors.toList());
            builder.lotes(lotesDTO);
        } else {
            builder.lotes(new ArrayList<>());
        }

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
