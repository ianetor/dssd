package com.dssd.backend.dtos;

import java.time.Instant;

public record OfertaResponseDTO(
                String id, Long emergenciaId, String nivelGravedad, Long loteId, String loteNombre,
                Integer cantidadOfrecida, String unidad, String tiempoLlegada, String observaciones,
                Instant fechaHora, String estado, String ongLider) {
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    public String getTipoRecursoNacional() {
        return com.dssd.backend.services.NombreRecursoNacional.obtener(loteNombre, unidad);
    }
}
