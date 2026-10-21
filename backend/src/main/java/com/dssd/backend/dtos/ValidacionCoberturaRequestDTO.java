package com.dssd.backend.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

/** Resultados obtenidos por Bonita de /api/validacion/competencias, uno por oferta activa. */
public record ValidacionCoberturaRequestDTO(
        @NotNull List<@NotNull @Valid ValidacionOferta> validaciones) {
    public record ValidacionOferta(@NotNull @Positive Long ofertaId, @NotNull @Valid Perfil perfil) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Perfil(@NotEmpty List<@NotNull @Valid Capacidad> capacidades) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Capacidad(@NotBlank String tipoRecurso,
            @NotNull @Positive Integer cantidadSolicitada,
            @NotNull @PositiveOrZero Integer cantidadDisponible,
            @NotNull RolPermitido rolPermitido) {}

    public enum RolPermitido { PRINCIPAL, APOYO, NO_HABILITADA }
}
