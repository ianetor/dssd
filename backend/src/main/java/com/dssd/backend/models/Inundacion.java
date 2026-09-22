package com.dssd.backend.models;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Entity
@DiscriminatorValue("INUNDACION")
@Getter
@Setter
public class Inundacion extends Emergencia {
    // Ejemplo de atributo específico
    private Double milimetrosAgua;
}
