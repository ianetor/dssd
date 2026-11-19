package com.dssd.backend.controllers;

import com.dssd.backend.config.AccesoCoordinador;
import com.dssd.backend.dtos.IndicadoresDTO;
import com.dssd.backend.services.IndicadoresService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Dashboard del Auditor / Directivo (el coordinador también puede consultarlo). */
@RestController
@RequestMapping("/api/directivo/indicadores")
public class IndicadoresController {
    private final IndicadoresService indicadores;
    private final AccesoCoordinador acceso;

    public IndicadoresController(IndicadoresService indicadores, AccesoCoordinador acceso) {
        this.indicadores = indicadores;
        this.acceso = acceso;
    }

    @GetMapping("/tiempo-revision")
    public IndicadoresDTO.TiempoEtapa tiempoRevision(HttpServletRequest request) {
        exigirDirectivo(request);
        return indicadores.tiempoRevision();
    }

    @GetMapping("/resumen")
    public IndicadoresDTO.Resumen resumen(HttpServletRequest request) {
        exigirDirectivo(request);
        return indicadores.resumen();
    }

    private void exigirDirectivo(HttpServletRequest request) {
        String rol = acceso.usuario(request).getRol();
        if (!List.of("AUDITOR_DIRECTIVO", "COORDINADOR_REGIONAL").contains(rol)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el personal directivo puede ver los indicadores");
        }
    }
}
