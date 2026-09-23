package com.dssd.backend.models;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Entity
@DiscriminatorValue("INCENDIO")
@Getter
@Setter
public class Incendio extends Emergencia {
    // Ejemplo de atributo específico que solo tiene sentido en un incendio
    private Double hectareasAfectadas;
}
