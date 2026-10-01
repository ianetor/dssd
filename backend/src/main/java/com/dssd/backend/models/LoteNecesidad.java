package com.dssd.backend.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(
    uniqueConstraints = @UniqueConstraint(
        name = "uk_lote_emergencia_tipo_recurso",
        columnNames = {"emergencia_id", "tipo_recurso"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoteNecesidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 254)
    @Column(name = "tipo_recurso", nullable = false, length = 254)
    private String tipoRecurso;

    @NotNull
    @Column(nullable = false)
    private Integer cantidadRequerida;

    // Inicia en 0 y se va sumando cuando se aprueban ofertas
    @Column(nullable = false)
    private Integer cantidadCubierta = 0;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "emergencia_id", nullable = false)
    private Emergencia emergencia;
}
