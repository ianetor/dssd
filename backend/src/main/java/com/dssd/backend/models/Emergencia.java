package com.dssd.model;

import jakarta.persistence.*;

@Entity
@Table(name = "emergencias")
public class Emergencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String titulo;
    private String descripcion;
    private String nivelGravedad;
    private String zonaAfectada;
    private String estado; // ej: "REGISTRADA", "EN_PROCESO"

    public Emergencia() {}

    public Emergencia(String titulo, String descripcion, String nivelGravedad, String zonaAfectada) {
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.nivelGravedad = nivelGravedad;
        this.zonaAfectada = zonaAfectada;
        this.estado = "REGISTRADA";
    }

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getNivelGravedad() { return nivelGravedad; }
    public void setNivelGravedad(String nivelGravedad) { this.nivelGravedad = nivelGravedad; }

    public String getZonaAfectada() { return zonaAfectada; }
    public void setZonaAfectada(String zonaAfectada) { this.zonaAfectada = zonaAfectada; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}