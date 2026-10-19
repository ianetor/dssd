package com.dssd.backend;

import com.dssd.backend.models.*;
import com.dssd.backend.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class EmergenciaOfertasTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmergenciaRepository emergenciaRepository;

    @Autowired
    private LoteNecesidadRepository loteRepository;

    @Autowired
    private OfertaAyudaRepository ofertaRepository;

    @Autowired
    private DetalleOfertaRepository detalleRepository;

    private Emergencia emergencia1;
    private Emergencia emergencia2;

    @BeforeEach
    void setUp() {
        // Emergencia 1 con caseId 5001
        Incendio e1 = new Incendio();
        e1.setDescripcion("Incendio en Delta");
        e1.setNivelGravedad("ALTO");
        e1.setZonaAfectada("Islas del Delta");
        e1.setEstado("CONVOCATORIA_ABIERTA");
        e1.setMunicipioNombre("Tigre");
        e1.setCaseId(5001L);
        e1.setHectareasAfectadas(120.0);
        emergencia1 = emergenciaRepository.save(e1);

        LoteNecesidad lote1 = new LoteNecesidad(null, "Agua mineral", 1000, 0, emergencia1);
        LoteNecesidad lote2 = new LoteNecesidad(null, "Mantas", 200, 0, emergencia1);
        loteRepository.saveAll(List.of(lote1, lote2));

        // Oferta 1 para Lote 1
        OfertaAyuda oferta1 = new OfertaAyuda();
        oferta1.setEstado("Registrada");
        oferta1.setOngLider("cruz_roja");
        oferta1.setUnidad("Litros");
        oferta1.setTiempoLlegada("2 horas");
        oferta1.setObservaciones("Disponibles de inmediato");
        oferta1.setFechaHora(Instant.now());
        ofertaRepository.save(oferta1);

        DetalleOferta d1 = new DetalleOferta(null, 500, oferta1, lote1);
        detalleRepository.save(d1);
        oferta1.getDetalles().add(d1);

        // Oferta 2 para Lote 2
        OfertaAyuda oferta2 = new OfertaAyuda();
        oferta2.setEstado("Rectificada");
        oferta2.setOngLider("cascos_blancos");
        oferta2.setUnidad("Unidades");
        oferta2.setTiempoLlegada("4 horas");
        oferta2.setObservaciones("En camino");
        oferta2.setFechaHora(Instant.now());
        ofertaRepository.save(oferta2);

        DetalleOferta d2 = new DetalleOferta(null, 150, oferta2, lote2);
        detalleRepository.save(d2);
        oferta2.getDetalles().add(d2);

        // Emergencia 2 con caseId 5002 (otra emergencia)
        Incendio e2 = new Incendio();
        e2.setDescripcion("Incendio en Cordoba");
        e2.setNivelGravedad("MEDIO");
        e2.setZonaAfectada("Valle de Punilla");
        e2.setEstado("CONVOCATORIA_ABIERTA");
        e2.setMunicipioNombre("Punilla");
        e2.setCaseId(5002L);
        e2.setHectareasAfectadas(50.0);
        emergencia2 = emergenciaRepository.save(e2);

        LoteNecesidad lote3 = new LoteNecesidad(null, "Herramientas", 50, 0, emergencia2);
        loteRepository.save(lote3);

        OfertaAyuda oferta3 = new OfertaAyuda();
        oferta3.setEstado("Registrada");
        oferta3.setOngLider("bomberos_voluntarios");
        oferta3.setUnidad("Kits");
        oferta3.setTiempoLlegada("1 hora");
        oferta3.setFechaHora(Instant.now());
        ofertaRepository.save(oferta3);

        DetalleOferta d3 = new DetalleOferta(null, 20, oferta3, lote3);
        detalleRepository.save(d3);
        oferta3.getDetalles().add(d3);
    }

    @Test
    @DisplayName("Debe listar todas las ofertas asociadas al caseId")
    void testObtenerOfertasPorCaseId() throws Exception {
        mockMvc.perform(get("/api/emergencias/caso/{caseId}/ofertas", 5001L)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.ongLider == 'cruz_roja')].cantidadOfrecida").value(500))
                .andExpect(jsonPath("$[?(@.ongLider == 'cruz_roja')].nivelGravedad").value("ALTO"))
                .andExpect(jsonPath("$[?(@.ongLider == 'cascos_blancos')].cantidadOfrecida").value(150))
                .andExpect(jsonPath("$[?(@.ongLider == 'cascos_blancos')].nivelGravedad").value("ALTO"));
    }

    @Test
    @DisplayName("Debe listar todas las ofertas asociadas al alias /case/{caseId}/ofertas")
    void testObtenerOfertasPorAliasCase() throws Exception {
        mockMvc.perform(get("/api/emergencias/case/{caseId}/ofertas", 5001L)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("Debe listar todas las ofertas por ID de emergencia")
    void testObtenerOfertasPorEmergenciaId() throws Exception {
        mockMvc.perform(get("/api/emergencias/{id}/ofertas", emergencia1.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("Debe retornar 404 si el caseId no existe")
    void testObtenerOfertasPorCaseIdInexistente() throws Exception {
        mockMvc.perform(get("/api/emergencias/caso/{caseId}/ofertas", 999999L)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Debe retornar 404 si el id de emergencia no existe")
    void testObtenerOfertasPorIdInexistente() throws Exception {
        mockMvc.perform(get("/api/emergencias/{id}/ofertas", 999999L)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
