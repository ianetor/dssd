package com.dssd.backend.dtos.EmergenciasDTO;

import com.dssd.backend.models.Emergencia;
import com.dssd.backend.models.Terremoto;

/**
 * TerremotoRequestDTO
 */
public class TerremotoRequestDTO extends EmergenciaRequestDTO {
    private Double magnitudRichter;

    @Override
    public Emergencia aEntidad() {
        Terremoto terremoto = new Terremoto();
        terremoto.setMagnitudRichter(magnitudRichter);
        return terremoto;
    }

}
