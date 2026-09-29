package com.dssd.backend.dtos.EmergenciasDTO;

import com.dssd.backend.models.Emergencia;
import com.dssd.backend.models.Terremoto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TerremotoRequestDTO extends EmergenciaRequestDTO {

    @Override
    public Emergencia aEntidad() {
        Terremoto terremoto = new Terremoto();
        terremoto.setMagnitudRichter(getMagnitudRichter());
        return terremoto;
    }
}
