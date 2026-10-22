package com.dssd.backend.models;

import com.dssd.backend.dtos.ValidacionCoberturaRequestDTO.RolPermitido;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Embeddable @Getter @Setter
public class CapacidadEvaluada {
    private String tipoRecurso;
    private Integer cantidadSolicitada;
    private Integer cantidadDisponible;
    @Enumerated(EnumType.STRING) private RolPermitido rolPermitido;
    private Integer nivel;
    private String riesgoMaximoPermitido;
    private String certificacion;
    @Column(columnDefinition = "text") private String motivo;
}
