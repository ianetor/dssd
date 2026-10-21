package com.dssd.backend.dtos;

import java.time.Instant;
import java.util.List;

public record ValidacionCoberturaDTO(Long emergenciaId, Long caseId, String estadoCobertura,
        boolean coberturaSuficiente, int umbralPorcentaje, int totalLotes, long lotesConCoberturaMinima,
        Instant fechaValidacion, List<LoteCobertura> lotes) {
    public record LoteCobertura(Long loteId, String tipoRecurso, int cantidadRequerida,
            long cantidadPrincipal, long cantidadApoyo, long cantidadHabilitada, double porcentajeCobertura,
            boolean coberturaMinimaAlcanzada) {}
}
