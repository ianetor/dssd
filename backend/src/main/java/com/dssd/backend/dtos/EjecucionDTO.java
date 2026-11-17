package com.dssd.backend.dtos;

import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Etapa de ejecución: despliegue de las ONGs adjudicadas, finalización y cierre. */
public final class EjecucionDTO {
    private EjecucionDTO() {}

    public record Recurso(String tipoRecurso, long cantidad, String unidad) {}

    public record Participacion(String ong, String estado, Instant fechaInicio, Instant fechaFinalizacion,
            String observaciones, List<Recurso> recursos) {}

    /** Tablero de monitoreo del Centro Coordinador. */
    public record Estado(Long emergenciaId, Long caseId, String estado, String zonaAfectada, String nivelGravedad,
            Instant fechaInicioEjecucion, Instant fechaCierreEjecucion, String coordinadorCierre,
            String motivoCierreEjecucion, int totalOngs, long ongsFinalizadas, boolean puedeCerrar,
            List<Participacion> participaciones) {}

    /** Notificación de adjudicación que ve cada ONG en su portal. */
    public record Adjudicacion(Long emergenciaId, String estadoEmergencia, String zonaAfectada, String nivelGravedad,
            String descripcion, Participacion participacion) {}

    public record FinalizacionRequest(@Size(max = 1000) String observaciones) {}

    public record CierreRequest(Boolean forzar, @Size(max = 1000) String motivo) {}
}
