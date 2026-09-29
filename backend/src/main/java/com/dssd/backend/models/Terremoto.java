package com.dssd.backend.models;

import com.dssd.backend.dtos.EmergenciasDTO.EmergenciaResponseDTO.EmergenciaResponseDTOBuilder;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Entity
@DiscriminatorValue("TERREMOTO")
@Getter
@Setter
public class Terremoto extends Emergencia {
    
    @Column(name = "magnitud_richter")
    private Double magnitudRichter;

    @Override
    public void popularCamposEspecificos(EmergenciaResponseDTOBuilder builder) {
        builder.tipoEmergencia("TERREMOTO");
        builder.magnitudRichter(this.magnitudRichter);
    }

        
}
