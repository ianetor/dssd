package com.dssd.backend.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity @Getter @Setter
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"lote_id", "oferta_origen_id"}))
public class OfertaEvaluada {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) private LoteEvaluado lote;
    @Column(nullable = false) private Long ofertaOrigenId;
    private int cantidadOfrecida;
    private String unidad;
    private String tiempoLlegada;
    @Column(columnDefinition = "text") private String observaciones;
    private Instant fechaHora;
    private String estadoAlCierre;
    private String ongLider;
    private String clasificacion;
    @Column(columnDefinition = "text") private String motivoExclusion;
    private String resultadoNacional;
    private String ongNombre;
    private Integer nivelHabilitacion;
    @ElementCollection
    @CollectionTable(name = "capacidad_evaluada", joinColumns = @JoinColumn(name = "oferta_evaluada_id"))
    @OrderColumn(name = "posicion")
    private List<CapacidadEvaluada> capacidades = new ArrayList<>();
}
