package com.dssd.backend.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DetalleOferta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(nullable = false)
    private Integer cantidadOfrecida;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "oferta_id", nullable = false)
    private OfertaAyuda oferta;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "lote_id", nullable = false)
    private LoteNecesidad lote;
}
