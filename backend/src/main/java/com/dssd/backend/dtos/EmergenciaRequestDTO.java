package com.dssd.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EmergenciaRequestDTO {
    private String tipoEmergencia;
    private String nivelGravedad;
    private String zonaAfectada;
    private String descripcion;
    private Long municipioId;

    private Double hectareasAfectadas;
    private Double milimetrosAgua;
    private Double magnitudRichter;
}
