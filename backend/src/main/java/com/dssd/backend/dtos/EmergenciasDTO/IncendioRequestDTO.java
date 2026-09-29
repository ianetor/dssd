package com.dssd.backend.dtos.EmergenciasDTO;

import com.dssd.backend.models.Emergencia;
import com.dssd.backend.models.Incendio;

/**
 * IncendioRequestDTO
 */
public class IncendioRequestDTO extends EmergenciaRequestDTO {

    private Double hectareasAfectadas;
    @Override
    public Emergencia aEntidad() {
        Incendio incendio = new Incendio();
        incendio.setHectareasAfectadas(this.hectareasAfectadas);
        return incendio;
    }
}

