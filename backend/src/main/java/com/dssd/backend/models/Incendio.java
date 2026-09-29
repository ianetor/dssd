package com.dssd.backend.models;

import com.dssd.backend.dtos.EmergenciasDTO.EmergenciaResponseDTO.EmergenciaResponseDTOBuilder;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Entity
@DiscriminatorValue("INCENDIO")
@Getter
@Setter
public class Incendio extends Emergencia {
    
    private Double hectareasAfectadas;

    @Override
    public void popularCamposEspecificos(EmergenciaResponseDTOBuilder builder) {
        builder.tipoEmergencia("INCENDIO");
        builder.hectareasAfectadas(this.hectareasAfectadas);
    }
    
}
