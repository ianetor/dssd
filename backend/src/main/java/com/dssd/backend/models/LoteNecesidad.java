package com.dssd.backend.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoteNecesidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String tipoRecurso;
    private Integer cantidadRequerida;
    
    // Inicia en 0 y se va sumando cuando se aprueban ofertas
    private Integer cantidadCubierta = 0; 

    @ManyToOne
    @JoinColumn(name = "emergencia_id")
    private Emergencia emergencia;
}
