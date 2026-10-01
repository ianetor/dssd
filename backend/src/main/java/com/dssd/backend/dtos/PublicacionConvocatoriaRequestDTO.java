package com.dssd.backend.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record PublicacionConvocatoriaRequestDTO(
        @NotNull @Min(1) @Max(2147483647L) Long duracionMinutos,
        @NotEmpty List<@NotNull @Valid LoteRequestDTO> lotes) {
}
