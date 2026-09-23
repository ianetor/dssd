package com.dssd.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoteRequestDTO {
    private String tipoRecurso;
    private Integer cantidadRequerida;
}
