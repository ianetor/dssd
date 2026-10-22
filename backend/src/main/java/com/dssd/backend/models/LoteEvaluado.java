package com.dssd.backend.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

@Entity @Getter @Setter
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"evaluacion_id", "lote_origen_id"}))
public class LoteEvaluado {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) private EvaluacionConvocatoria evaluacion;
    @Column(nullable = false) private Long loteOrigenId;
    private String tipoRecurso;
    private int cantidadRequerida;
    private int cantidadOfertadaAlCierre;
    private long cantidadPrincipal;
    private long cantidadApoyo;
    @OneToMany(mappedBy = "lote", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ofertaOrigenId ASC")
    private List<OfertaEvaluada> ofertas = new ArrayList<>();
}
