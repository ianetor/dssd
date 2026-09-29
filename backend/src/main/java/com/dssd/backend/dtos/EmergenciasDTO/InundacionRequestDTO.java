package com.dssd.backend.dtos.EmergenciasDTO;

import com.dssd.backend.models.Emergencia;
import com.dssd.backend.models.Inundacion;

/**
 * InundacionRequestDTO
 */
public class InundacionRequestDTO extends EmergenciaRequestDTO{

    private Double milimetrosAgua;

    @Override
    public Emergencia aEntidad() {
        Inundacion inundacion = new Inundacion();
        inundacion.setMilimetrosAgua(this.milimetrosAgua);
        return inundacion;
    }

}
