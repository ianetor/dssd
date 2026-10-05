package com.dssd.backend.dtos;

public record CoberturaConvocatoriaDTO(Long emergenciaId, Long caseId,
        boolean coberturaCompleta, long totalLotes, long lotesSinCubrir,
        String estado, String motivoCierre) {}
