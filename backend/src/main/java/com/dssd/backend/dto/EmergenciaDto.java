package com.dssd.dto;

public class EmergenciaDto {
    private String titulo;
    private String descripcion;
    private String nivelGravedad;
    private String zonaAfectada;

    // Getters y Setters
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getNivelGravedad() { return nivelGravedad; }
    public void setNivelGravedad(String nivelGravedad) { this.nivelGravedad = nivelGravedad; }

    public String getZonaAfectada() { return zonaAfectada; }
    public void setZonaAfectada(String zonaAfectada) { this.zonaAfectada = zonaAfectada; }
}