package com.dssd.backend.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.ArrayList;
import java.util.List;

import com.dssd.backend.dtos.EmergenciasDTO.EmergenciaResponseDTO;

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "tipo_emergencia", discriminatorType = DiscriminatorType.STRING)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public abstract class Emergencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nivelGravedad;
    private String zonaAfectada;
    private String descripcion;
    private String estado;
    private String municipioNombre;
    private Long caseId;

    @OneToMany(mappedBy = "emergencia", cascade = {CascadeType.REMOVE, CascadeType.PERSIST, CascadeType.MERGE}, orphanRemoval = true)
    private List<LoteNecesidad> lotes = new ArrayList<>();

    public abstract void popularCamposEspecificos(EmergenciaResponseDTO.EmergenciaResponseDTOBuilder builder);
}
