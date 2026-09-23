package com.dssd.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MunicipioResponseDTO {
    private Long id;
    private String nombre;
    private String bonitaUsername;
}
