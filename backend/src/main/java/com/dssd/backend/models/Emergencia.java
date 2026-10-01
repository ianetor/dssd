package com.dssd.backend.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String nivelGravedad;

    @NotBlank
    @Size(max = 255)
    @Column(name = "zona_afectada", nullable = false, length = 255)
    private String zonaAfectada;

    @NotBlank
    @Size(max = 1000)
    @Column(nullable = false, length = 1000)
    private String descripcion;

    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String estado;

    @NotBlank
    @Size(max = 255)
    @Column(name = "municipio_nombre", nullable = false, length = 255)
    private String municipioNombre;

    private Long caseId;

    @OneToMany(mappedBy = "emergencia", cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE}, orphanRemoval = true)
    private List<LoteNecesidad> lotes = new ArrayList<>();

    public abstract void popularCamposEspecificos(EmergenciaResponseDTO.EmergenciaResponseDTOBuilder builder);
}
