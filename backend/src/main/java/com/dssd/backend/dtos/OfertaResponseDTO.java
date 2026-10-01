package com.dssd.backend.dtos;

import java.time.Instant;

public record OfertaResponseDTO(
                String id, Long emergenciaId, Long loteId, String loteNombre,
                Integer cantidadOfrecida, String unidad, String tiempoLlegada, String observaciones,
                Instant fechaHora, String estado, String ongLider) {
}
