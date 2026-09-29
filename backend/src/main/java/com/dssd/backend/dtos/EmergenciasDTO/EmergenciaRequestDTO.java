package com.dssd.backend.dtos.EmergenciasDTO;

import com.dssd.backend.models.Emergencia;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "tipoEmergencia", // Campo que envía Angular
    visible = true
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = IncendioRequestDTO.class, name = "INCENDIO"),
    @JsonSubTypes.Type(value = InundacionRequestDTO.class, name = "INUNDACION"),
    @JsonSubTypes.Type(value = TerremotoRequestDTO.class, name = "TERREMOTO")
})
public  abstract class EmergenciaRequestDTO {
    private String tipoEmergencia;
    private String nivelGravedad;
    private String zonaAfectada;
    private String descripcion;
    private String municipioNombre;

    private Double hectareasAfectadas;
    private Double milimetrosAgua;
    private Double magnitudRichter;

    public abstract Emergencia aEntidad();

    public String getTipoEmergencia(){
        return tipoEmergencia;
    }

    protected void cargarCamposComunes(Emergencia emergencia) {
        emergencia.setNivelGravedad(this.nivelGravedad);
        emergencia.setZonaAfectada(this.zonaAfectada);
        emergencia.setDescripcion(this.descripcion);
        emergencia.setMunicipioNombre(this.municipioNombre);
        emergencia.setEstado("REGISTRADA");
    }

}
