package com.dssd.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioResponseDTO {
    private Long id;
    private String username;
    private String nombreCompleto;
    private String rol; // OPERADOR_MUNICIPAL, COORDINADOR_REGIONAL, REPRESENTANTE_ONG, AUDITOR_DIRECTIVO
    private String rolDisplayName;
    private Long entidadId; // ID del Municipio o de la ONG si corresponde
    private String entidadNombre; // Nombre del Municipio o de la ONG
}
