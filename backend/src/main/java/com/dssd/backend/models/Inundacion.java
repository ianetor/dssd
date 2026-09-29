package com.dssd.backend.models;

import com.dssd.backend.dtos.EmergenciasDTO.EmergenciaResponseDTO.EmergenciaResponseDTOBuilder;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Entity
@DiscriminatorValue("INUNDACION")
@Getter
@Setter
public class Inundacion extends Emergencia {
    
    private Double milimetrosAgua;

    @Override
    public void popularCamposEspecificos(EmergenciaResponseDTOBuilder builder) {
        builder.tipoEmergencia("INUNDACION");
        builder.milimetrosAgua(this.milimetrosAgua);
    }
    
}
