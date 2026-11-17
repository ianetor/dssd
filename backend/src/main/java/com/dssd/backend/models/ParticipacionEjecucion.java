package com.dssd.backend.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Participación de una ONG adjudicada en la ejecución de una emergencia. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_participacion_emergencia_ong", columnNames = {"emergencia_id", "ong"}))
@Getter
@Setter
@NoArgsConstructor
public class ParticipacionEjecucion {

    public static final String EN_CURSO = "EN_CURSO";
    public static final String FINALIZADA = "FINALIZADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "emergencia_id", nullable = false)
    private Emergencia emergencia;

    /** Username de la ONG (el mismo valor que OfertaAyuda.ongLider). */
    @Column(nullable = false, length = 254)
    private String ong;

    @Column(nullable = false, length = 20)
    private String estado = EN_CURSO;

    @Column(nullable = false)
    private Instant fechaInicio;

    private Instant fechaFinalizacion;

    @Column(length = 1000)
    private String observaciones;

    public boolean finalizada() {
        return FINALIZADA.equals(estado);
    }
}
