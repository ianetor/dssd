package com.dssd.backend.dtos.EmergenciasDTO;

import com.dssd.backend.models.Emergencia;
import com.dssd.backend.models.Incendio;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IncendioRequestDTO extends EmergenciaRequestDTO {

    @Override
    public Emergencia aEntidad() {
        Incendio incendio = new Incendio();
        incendio.setHectareasAfectadas(getHectareasAfectadas());
        return incendio;
    }
}
