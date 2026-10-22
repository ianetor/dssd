package com.dssd.backend.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter @Setter
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"emergencia_id", "ronda"}))
public class EvaluacionConvocatoria {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Emergencia emergencia;
    @Column(nullable = false) private Integer ronda;
    @Column(nullable = false) private Instant fecha;
    @Column(nullable = false) private boolean depurada;
    @Embedded private EmergenciaEvaluada datosEmergencia;
    private Integer umbralPorcentaje;
    @OneToMany(mappedBy = "evaluacion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("loteOrigenId ASC")
    private List<LoteEvaluado> lotes = new ArrayList<>();
    // Solo lectura de datos anteriores a la normalización. Se vacían al migrar.
    @Column(columnDefinition = "text") private String datosJson;
    @Column(columnDefinition = "text") private String validacionesJson;
    @Column(columnDefinition = "text") private String resultadoJson;
}
