package com.dssd.backend.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Datos necesarios para registrar o rectificar una oferta de ayuda. */
public record OfertaRequestDTO(
        @NotNull Long emergenciaId,
        @NotNull Long loteId,
        @NotNull @Min(1) Integer cantidadOfrecida,
        @NotBlank String unidad,
        @NotBlank String tiempoLlegada,
        String observaciones) {
}
