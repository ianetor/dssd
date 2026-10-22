package com.dssd.backend.dtos;
import jakarta.validation.constraints.*;
public record DecisionCoberturaRequestDTO(@NotNull @Positive Integer ronda,
        @NotNull Accion decision, @NotBlank @Size(max = 1000) String motivo,
        @Min(1) @Max(2147483647L) Long duracionMinutos) {
    public enum Accion { REABRIR, CANCELAR }
}
