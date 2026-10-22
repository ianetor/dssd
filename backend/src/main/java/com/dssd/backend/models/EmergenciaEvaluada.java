package com.dssd.backend.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

/** Datos de la emergencia que deben permanecer tal como estaban al cerrar la ronda. */
@Embeddable @Getter @Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmergenciaEvaluada {
    private Long caseId;
    private String tipoEmergencia;
    private String nivelGravedad;
    private String zonaAfectada;
    @Column(columnDefinition = "text") private String descripcion;
    private String estado;
    private String municipioNombre;
    private Long duracionConvocatoriaMinutos;
    private Instant fechaAperturaConvocatoria;
    private Instant fechaVencimientoConvocatoria;
    private Instant fechaCierreConvocatoria;
    private String motivoCierre;
    private Double hectareasAfectadas;
    private Double milimetrosAgua;
    private Double magnitudRichter;
}
