package com.dssd.backend.controllers;

import com.dssd.backend.config.AccesoCoordinador;
import com.dssd.backend.services.SistemaNacionalClient;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Catálogo del Sistema Nacional para que el Centro Coordinador defina los lotes. */
@RestController
@RequestMapping("/api/coordinador")
public class CatalogoNacionalController {
    private final AccesoCoordinador acceso;
    private final SistemaNacionalClient nacional;

    public CatalogoNacionalController(AccesoCoordinador acceso, SistemaNacionalClient nacional) {
        this.acceso = acceso;
        this.nacional = nacional;
    }

    @GetMapping("/catalogo-recursos")
    public List<SistemaNacionalClient.RecursoNacional> recursos(HttpServletRequest request) {
        acceso.exigir(request);
        return nacional.disponibilidad();
    }
}
