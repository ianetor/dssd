package com.dssd.backend;

import com.dssd.backend.config.AccesoCoordinador;
import com.dssd.backend.dtos.UsuarioResponseDTO;
import com.dssd.backend.models.DetalleOferta;
import com.dssd.backend.models.Incendio;
import com.dssd.backend.models.Inundacion;
import com.dssd.backend.models.LoteNecesidad;
import com.dssd.backend.models.OfertaAyuda;
import com.dssd.backend.repositories.DetalleOfertaRepository;
import com.dssd.backend.repositories.LoteNecesidadRepository;
import com.dssd.backend.repositories.OfertaAyudaRepository;
import com.dssd.backend.repositories.EmergenciaRepository;
import com.dssd.backend.services.BonitaService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IndicadoresTest {
    private static final String URL = "/api/directivo/indicadores/tiempo-revision";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired MockMvc mvc;
    @Autowired EmergenciaRepository emergencias;
    @Autowired LoteNecesidadRepository lotes;
    @Autowired OfertaAyudaRepository ofertas;
    @Autowired DetalleOfertaRepository detalles;
    @MockitoBean BonitaService bonita;

    @BeforeEach
    void preparar() {
        Incendio incendio = new Incendio();
        incendio.setDescripcion("d"); incendio.setNivelGravedad("ALTA"); incendio.setZonaAfectada("z");
        incendio.setEstado("FINALIZADA"); incendio.setMunicipioNombre("Tandil"); incendio.setCaseId(9001L);
        incendio.setFechaInicioEjecucion(Instant.parse("2026-11-16T12:00:00Z"));
        emergencias.save(incendio);
        // Paramedicos lo cubren dos ONGs; Brigadistas una sola. La oferta retirada no cuenta.
        LoteNecesidad medicos = lotes.save(new LoteNecesidad(null, "Paramedicos (unidades)", 10, 0, incendio));
        LoteNecesidad brigada = lotes.save(new LoteNecesidad(null, "Brigadistas (unidades)", 5, 0, incendio));
        oferta("april.sanchez", "Registrada", medicos, 6);
        oferta("helen.kelly", "Validada", medicos, 4);
        oferta("helen.kelly", "Rectificada", brigada, 5);
        oferta("jan.fisher", "Retirada", brigada, 9);
        Inundacion inundacion = new Inundacion();
        inundacion.setDescripcion("d"); inundacion.setNivelGravedad("MEDIA"); inundacion.setZonaAfectada("z");
        inundacion.setEstado("EN_EJECUCION"); inundacion.setMunicipioNombre("Lujan"); inundacion.setCaseId(9002L);
        emergencias.save(inundacion);
        Incendio sinRevisar = new Incendio();
        sinRevisar.setDescripcion("d"); sinRevisar.setNivelGravedad("BAJA"); sinRevisar.setZonaAfectada("z");
        sinRevisar.setEstado("REGISTRADA"); sinRevisar.setMunicipioNombre("Azul"); sinRevisar.setCaseId(9003L);
        emergencias.save(sinRevisar);

        // Igual que Bonita: una fila archivada por cada estado que deja la tarea.
        when(bonita.historialDelCaso(9001L)).thenReturn(List.of(
                fila("Revisar Emergencia y Generar Lotes", 1, "initializing", "2026-11-16 10:00:00.000"),
                fila("Revisar Emergencia y Generar Lotes", 1, "ready", "2026-11-16 10:01:30.000"),
                fila("Revisar Emergencia y Generar Lotes", 1, "completed", "2026-11-16 10:01:30.250"),
                fila("Realizar Ofertas", 2, "completed", "2026-11-16 12:00:00.000")));
        when(bonita.historialDelCaso(9002L)).thenReturn(List.of(
                fila("Revisar Emergencia y Generar Lotes", 5, "initializing", "2026-11-16 11:00:00"),
                fila("Revisar Emergencia y Generar Lotes", 5, "completed", "2026-11-16 11:05:00")));
        // Tarea todavía abierta: no se mide.
        when(bonita.historialDelCaso(9003L)).thenReturn(new ArrayList<>(List.of(
                fila("Revisar Emergencia y Generar Lotes", 9, "initializing", "2026-11-16 13:00:00"))));
    }

    private void oferta(String ong, String estado, LoteNecesidad lote, int cantidad) {
        OfertaAyuda o = new OfertaAyuda();
        o.setEstado(estado); o.setOngLider(ong); o.setUnidad("unidades"); o.setFechaHora(Instant.now());
        ofertas.save(o);
        o.getDetalles().add(detalles.save(new DetalleOferta(null, cantidad, o, lote)));
    }

    private static JsonNode fila(String nombre, long fuente, String estado, String fecha) {
        return JSON.createObjectNode().put("name", nombre).put("sourceObjectId", String.valueOf(fuente))
                .put("state", estado).put("archivedDate", fecha);
    }

    private static UsuarioResponseDTO usuario(String rol) {
        return UsuarioResponseDTO.builder().id(1L).username("x").rol(rol).build();
    }

    @Test
    @DisplayName("Calcula el promedio cruzando las emergencias locales con el historial de Bonita")
    void promedioPorTipo() throws Exception {
        mvc.perform(get(URL).sessionAttr(AccesoCoordinador.USUARIO, usuario("AUDITOR_DIRECTIVO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidad", is(2)))
                .andExpect(jsonPath("$.casosSinDatos", is(1)))
                .andExpect(jsonPath("$.promedioSegundos", closeTo(195.125, 0.001)))
                .andExpect(jsonPath("$.minimoSegundos", closeTo(90.25, 0.001)))
                .andExpect(jsonPath("$.porTipo", hasSize(2)))
                .andExpect(jsonPath("$.porTipo[0].tipoEmergencia", is("INCENDIO")))
                .andExpect(jsonPath("$.porTipo[1].tipoEmergencia", is("INUNDACION")))
                .andExpect(jsonPath("$.mediciones[0].municipio", is("Tandil")));
    }

    @Test
    @DisplayName("El resumen cuenta emergencias por estado y suma los suministros de las que entraron en ejecución")
    void resumen() throws Exception {
        mvc.perform(get("/api/directivo/indicadores/resumen").sessionAttr(AccesoCoordinador.USUARIO, usuario("AUDITOR_DIRECTIVO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estados", hasSize(3)))
                .andExpect(jsonPath("$.estados[0].estado", is("EN_EJECUCION")))
                .andExpect(jsonPath("$.estados[0].cantidad", is(1)))
                // Solo el incendio inició ejecución; la oferta retirada de jan.fisher no se suma.
                .andExpect(jsonPath("$.suministros", hasSize(2)))
                .andExpect(jsonPath("$.suministros[0].mes", is("2026-11")))
                .andExpect(jsonPath("$.suministros[0].tipoRecurso", is("Brigadistas")))
                .andExpect(jsonPath("$.suministros[0].cantidad", is(5)))
                .andExpect(jsonPath("$.suministros[1].tipoRecurso", is("Paramedicos")))
                .andExpect(jsonPath("$.suministros[1].cantidad", is(10)));
    }

    @Test
    @DisplayName("Una ONG no puede ver los indicadores")
    void soloDirectivos() throws Exception {
        mvc.perform(get(URL).sessionAttr(AccesoCoordinador.USUARIO, usuario("REPRESENTANTE_ONG")))
                .andExpect(status().isForbidden());
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }
}
