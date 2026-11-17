package com.dssd.backend;

import com.dssd.backend.config.AccesoCoordinador;
import com.dssd.backend.dtos.UsuarioResponseDTO;
import com.dssd.backend.models.*;
import com.dssd.backend.repositories.*;
import com.dssd.backend.services.BonitaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "BONITA_INTEGRATION_KEY=clave-de-prueba")
@AutoConfigureMockMvc
@Transactional
class EjecucionTest {
    private static final long CASO = 7001L;
    private static final String CLAVE = "clave-de-prueba";

    @Autowired MockMvc mvc;
    @Autowired EmergenciaRepository emergencias;
    @Autowired LoteNecesidadRepository lotes;
    @Autowired OfertaAyudaRepository ofertas;
    @Autowired DetalleOfertaRepository detalles;
    @MockitoBean BonitaService bonita;

    private Long emergenciaId;

    @BeforeEach
    void preparar() {
        Incendio e = new Incendio();
        e.setDescripcion("Incendio forestal");
        e.setNivelGravedad("ALTO");
        e.setZonaAfectada("Sierras");
        e.setEstado("CONVOCATORIA_CERRADA");
        e.setMunicipioNombre("Tandil");
        e.setCaseId(CASO);
        e.setHectareasAfectadas(50.0);
        emergenciaId = emergencias.save(e).getId();
        LoteNecesidad agua = lotes.save(new LoteNecesidad(null, "Agua (Litros)", 1000, 0, e));
        LoteNecesidad brigada = lotes.save(new LoteNecesidad(null, "Brigadistas", 10, 0, e));
        oferta("april.sanchez", "Registrada", "Litros", agua, 600);
        oferta("april.sanchez", "Rectificada", null, brigada, 4);
        oferta("helen.kelly", "Validada", null, brigada, 6);
        oferta("otra.ong", "Retirada", null, brigada, 2);
    }

    private void oferta(String ong, String estado, String unidad, LoteNecesidad lote, int cantidad) {
        OfertaAyuda o = new OfertaAyuda();
        o.setEstado(estado);
        o.setOngLider(ong);
        o.setUnidad(unidad);
        o.setFechaHora(Instant.now());
        ofertas.save(o);
        o.getDetalles().add(detalles.save(new DetalleOferta(null, cantidad, o, lote)));
    }

    private UsuarioResponseDTO usuario(String rol) {
        return UsuarioResponseDTO.builder().id(1L).username("coordinador").rol(rol).build();
    }

    private void iniciar() throws Exception {
        mvc.perform(post("/api/integracion/casos/" + CASO + "/ejecucion/iniciar").header("X-Integration-Key", CLAVE))
                .andExpect(status().isOk());
    }

    private void finalizar(String ong) throws Exception {
        mvc.perform(post("/api/ejecucion/emergencias/" + emergenciaId + "/finalizacion")
                        .header("X-ONG-Username", ong).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observaciones\":\"Sin novedades\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado", is("FINALIZADA")));
    }

    @Test
    @DisplayName("La notificación de adjudicación inicia la ejecución solo con las ofertas activas")
    void iniciaEjecucion() throws Exception {
        mvc.perform(post("/api/integracion/casos/" + CASO + "/ejecucion/iniciar"))
                .andExpect(status().isUnauthorized());
        iniciar();
        iniciar(); // idempotente: Bonita puede reintentar el conector

        mvc.perform(get("/api/coordinador/emergencias/" + emergenciaId + "/ejecucion")
                        .sessionAttr(AccesoCoordinador.USUARIO, usuario("COORDINADOR_REGIONAL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("EN_EJECUCION")))
                .andExpect(jsonPath("$.totalOngs", is(2)))
                .andExpect(jsonPath("$.puedeCerrar", is(false)))
                .andExpect(jsonPath("$.participaciones[0].ong", is("april.sanchez")))
                .andExpect(jsonPath("$.participaciones[0].recursos", hasSize(2)))
                .andExpect(jsonPath("$.participaciones[0].recursos[0].tipoRecurso", is("Agua")))
                .andExpect(jsonPath("$.participaciones[0].recursos[0].cantidad", is(600)));

        mvc.perform(get("/api/ejecucion/adjudicaciones").header("X-ONG-Username", "helen.kelly"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].participacion.estado", is("EN_CURSO")));
        mvc.perform(get("/api/ejecucion/adjudicaciones").header("X-ONG-Username", "otra.ong"))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/coordinador/emergencias/" + emergenciaId + "/ejecucion")
                        .sessionAttr(AccesoCoordinador.USUARIO, usuario("REPRESENTANTE_ONG")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Con ONGs en curso y sin forzar, el coordinador no puede cerrar")
    void noCierraConOngsEnCurso() throws Exception {
        iniciar();
        finalizar("april.sanchez");
        mvc.perform(post("/api/coordinador/emergencias/" + emergenciaId + "/ejecucion/cierre")
                        .sessionAttr(AccesoCoordinador.USUARIO, usuario("COORDINADOR_REGIONAL")))
                .andExpect(status().isConflict());
        verify(bonita, never()).completarMonitoreo(CASO);
    }

    @Test
    @DisplayName("El cierre forzado exige un motivo y lo deja registrado")
    void cierreForzado() throws Exception {
        iniciar();
        finalizar("april.sanchez");
        String url = "/api/coordinador/emergencias/" + emergenciaId + "/ejecucion/cierre";
        mvc.perform(post(url).sessionAttr(AccesoCoordinador.USUARIO, usuario("COORDINADOR_REGIONAL"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"forzar\":true,\"motivo\":\"  \"}"))
                .andExpect(status().isConflict());
        verify(bonita, never()).completarMonitoreo(CASO);
        mvc.perform(post(url).sessionAttr(AccesoCoordinador.USUARIO, usuario("COORDINADOR_REGIONAL"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"forzar\":true,\"motivo\":\"La ONG no responde\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("CIERRE_PENDIENTE")))
                .andExpect(jsonPath("$.motivoCierreEjecucion", is("La ONG no responde")))
                .andExpect(jsonPath("$.coordinadorCierre", is("coordinador")));
        verify(bonita).completarMonitoreo(CASO);
    }

    @Test
    @DisplayName("Con todas las ONGs finalizadas se completa el monitoreo y Bonita cierra la emergencia")
    void cierreCompleto() throws Exception {
        iniciar();
        finalizar("april.sanchez");
        finalizar("april.sanchez"); // idempotente
        finalizar("helen.kelly");
        mvc.perform(post("/api/ejecucion/emergencias/" + emergenciaId + "/finalizacion")
                        .header("X-ONG-Username", "otra.ong"))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/coordinador/emergencias/" + emergenciaId + "/ejecucion/cierre")
                        .sessionAttr(AccesoCoordinador.USUARIO, usuario("COORDINADOR_REGIONAL")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado", is("CIERRE_PENDIENTE")));
        verify(bonita).completarMonitoreo(CASO);

        // Bonita liberó en el Sistema Nacional y avisa al backend local.
        mvc.perform(post("/api/integracion/casos/" + CASO + "/ejecucion/cerrar").header("X-Integration-Key", CLAVE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado", is("FINALIZADA")));
        mvc.perform(post("/api/integracion/casos/" + CASO + "/ejecucion/cerrar").header("X-Integration-Key", CLAVE))
                .andExpect(status().isOk());
        List<OfertaAyuda> todas = ofertas.findByEmergenciaId(emergenciaId);
        org.junit.jupiter.api.Assertions.assertTrue(todas.stream().noneMatch(OfertaAyuda::getRecursosBloqueados));
    }
}
