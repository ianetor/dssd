package com.dssd.backend.dtos.EmergenciasDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import com.dssd.backend.dtos.LoteResponseDTO;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmergenciaResponseDTO {
    private Long id;
    private String tipoEmergencia;
    private String nivelGravedad;
    private String zonaAfectada;
    private String descripcion;
    private String estado;

    private String municipioNombre;

    private Double hectareasAfectadas;
    private Double milimetrosAgua;
    private Double magnitudRichter;

    @Builder.Default
    private List<LoteResponseDTO> lotes = new ArrayList<>();
}
