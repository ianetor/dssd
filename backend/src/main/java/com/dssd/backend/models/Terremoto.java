package com.dssd.backend.models;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Entity
@DiscriminatorValue("TERREMOTO")
@Getter
@Setter
public class Terremoto extends Emergencia {
    // Ejemplo de atributo específico
    private Double magnitudRichter;
}
