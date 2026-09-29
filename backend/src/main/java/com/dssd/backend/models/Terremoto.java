package com.dssd.backend.models;

import com.dssd.backend.dtos.EmergenciasDTO.EmergenciaResponseDTO.EmergenciaResponseDTOBuilder;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Entity
@DiscriminatorValue("TERREMOTO")
@Getter
@Setter
public class Terremoto extends Emergencia {
    
    private Double magnitudRichter;

    @Override
    public void popularCamposEspecificos(EmergenciaResponseDTOBuilder builder) {
        builder.magnitudRichter(this.magnitudRichter);
    }

        
}
