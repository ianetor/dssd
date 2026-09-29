package com.dssd.backend.dtos.EmergenciasDTO;

import com.dssd.backend.models.Emergencia;
import com.dssd.backend.models.Inundacion;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InundacionRequestDTO extends EmergenciaRequestDTO {

    @Override
    public Emergencia aEntidad() {
        Inundacion inundacion = new Inundacion();
        inundacion.setMilimetrosAgua(getMilimetrosAgua());
        return inundacion;
    }
}
