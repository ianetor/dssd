package com.dssd.backend.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String estado;

    @NotNull
    @Column(nullable = false)
    private Integer nivelHabilitacion;

    @Column(nullable = false)
    private Boolean recursosBloqueados = false;

    @Size(max = 254)
    @Column(length = 254)
    private String ongLider;
    private String unidad;
    private String tiempoLlegada;
    @Column(length = 4000)
    private String observaciones;
    private java.time.Instant fechaHora;

    @OneToMany(mappedBy = "oferta", cascade = { CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE }, orphanRemoval = true)
    private List<DetalleOferta> detalles = new ArrayList<>();
}
