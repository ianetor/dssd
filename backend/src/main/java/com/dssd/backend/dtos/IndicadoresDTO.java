package com.dssd.backend.dtos;

import java.time.LocalDateTime;
import java.util.List;

/** Indicadores para el Auditor / Directivo: datos locales cruzados con el historial de Bonita. */
public final class IndicadoresDTO {
    private IndicadoresDTO() {}

    /** Una ejecución de la tarea medida (una emergencia puede tener más de una si se reformula). */
    public record Medicion(Long emergenciaId, Long caseId, String tipoEmergencia, String municipio,
            LocalDateTime inicio, LocalDateTime fin, double segundos) {}

    public record PorTipo(String tipoEmergencia, double promedioSegundos, int cantidad) {}

    public record EstadoCantidad(String estado, long cantidad) {}

    /** Unidades comprometidas en emergencias que entraron en ejecución, por mes y recurso. */
    public record SuministroMes(String mes, String tipoRecurso, String unidad, long cantidad) {}

    public record Resumen(List<EstadoCantidad> estados, List<SuministroMes> suministros) {}

    public record TiempoEtapa(String etapa, double promedioSegundos, double minimoSegundos, double maximoSegundos,
            int cantidad, int casosSinDatos, List<PorTipo> porTipo, List<Medicion> mediciones) {}
}
