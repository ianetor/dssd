package com.dssd.backend.services;

import com.dssd.backend.dtos.MunicipioResponseDTO;
import com.dssd.backend.models.Municipio;
import com.dssd.backend.repositories.MunicipioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class MunicipioService {

    private final MunicipioRepository municipioRepository;

    public MunicipioService(MunicipioRepository municipioRepository) {
        this.municipioRepository = municipioRepository;
    }

    public List<MunicipioResponseDTO> listarMunicipios() {
        return municipioRepository.findAll().stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    public Optional<Municipio> obtenerPorId(Long id) {
        return municipioRepository.findById(id);
    }

    private MunicipioResponseDTO toResponseDTO(Municipio municipio) {
        return MunicipioResponseDTO.builder()
                .id(municipio.getId())
                .nombre(municipio.getNombre())
                .bonitaUsername(municipio.getBonitaUsername())
                .build();
    }
}
