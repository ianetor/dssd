package com.dssd.backend.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OfertaAyuda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String estado;
    private Integer nivelHabilitacion;
    private Boolean recursosBloqueados = false;
    private String ongLider;
    private List<String> ongColaboradoras = new ArrayList<>();


    @OneToMany(mappedBy = "oferta", cascade = { CascadeType.REMOVE, CascadeType.PERSIST,
            CascadeType.MERGE }, orphanRemoval = true)
    private List<DetalleOferta> detalles = new ArrayList<>();
}
