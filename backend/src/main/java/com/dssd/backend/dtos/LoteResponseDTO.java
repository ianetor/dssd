package com.dssd.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoteResponseDTO {
    private Long id;
    private String tipoRecurso;
    private Integer cantidadRequerida;
    private Integer cantidadCubierta;
}
