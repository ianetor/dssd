package com.dssd.backend.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Getter @Setter
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"emergencia_id", "ronda"}))
public class DecisionCobertura {
    @Id private String id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) private Emergencia emergencia;
    @Column(nullable = false) private Integer ronda;
    @Column(nullable = false) private String decision;
    @Column(nullable = false, length = 1000) private String motivo;
    @Column(nullable = false) private String coordinador;
    private Long coordinadorId;
    @Column(nullable = false) private Instant fecha;
    private Long duracionMinutos;
    @Column(nullable = false) private Long tareaBonitaId;
    @Column(nullable = false) private String estado;
    private int intentos;
    private Instant proximoIntento;
    @Column(length = 1000) private String error;
    @Column(columnDefinition = "text") private String aplicadaJson;
}
