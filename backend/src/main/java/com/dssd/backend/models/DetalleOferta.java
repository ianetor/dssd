package com.dssd.backend.models;

import jakarta.persistence.*;
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

    private Integer cantidadOfrecida;

    @ManyToOne
    @JoinColumn(name = "oferta_id")
    private OfertaAyuda oferta;

    @ManyToOne
    @JoinColumn(name = "lote_id")
    private LoteNecesidad lote;
}
