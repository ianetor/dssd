package com.dssd.backend.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.ArrayList;
import java.util.List;

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

    @ManyToOne
    @JoinColumn(name = "municipio_id")
    private Municipio municipioAfectado;

    @OneToMany(mappedBy = "emergencia", cascade = { CascadeType.REMOVE }, orphanRemoval = true)
    private List<LoteNecesidad> lotes = new ArrayList<>();
}
