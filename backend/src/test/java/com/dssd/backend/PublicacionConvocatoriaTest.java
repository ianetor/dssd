package com.dssd.backend;

import com.dssd.backend.dtos.*;
import com.dssd.backend.models.*;
import com.dssd.backend.repositories.EmergenciaRepository;
import com.dssd.backend.services.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:publicacion;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
        "convocatoria.publicacion.worker-enabled=false"
})
@AutoConfigureMockMvc
class PublicacionConvocatoriaTest {
    @Autowired EmergenciaRepository repo;
    @Autowired EmergenciaService servicio;
    @Autowired PlatformTransactionManager tm;
    @Autowired MockMvc mvc;
    @MockitoBean BonitaService bonita;
    @MockitoBean AuthService auth;
    @MockitoBean BonitaClientService bonitaClient;
    Long id;
    PublicacionConvocatoriaWorker worker;

    @BeforeEach void preparar() {
        Incendio e = new Incendio();
        e.setDescripcion("Prueba publicación"); e.setEstado("REGISTRADA");
        e.setNivelGravedad("ALTO"); e.setZonaAfectada("Norte"); e.setMunicipioNombre("Municipio");
        e.setCaseId(10L);
        id = repo.saveAndFlush(e).getId();
        worker = new PublicacionConvocatoriaWorker(repo, bonita, tm);
        when(bonita.buscarTareaPublicacion(10L)).thenReturn(20L);
    }

    PublicacionConvocatoriaRequestDTO solicitud(long minutos) {
        return new PublicacionConvocatoriaRequestDTO(minutos, List.of(new LoteRequestDTO("Agua (litros)", 100)));
    }

    @Test void solicitudDurableDevuelve202SinContactarBonita() throws Exception {
        mvc.perform(post("/api/emergencias/{id}/lotes", id).contentType("application/json")
                .content("{\"duracionMinutos\":120,\"lotes\":[{\"tipoRecurso\":\"Agua (litros)\",\"cantidadRequerida\":100}]}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.estado").value("PUBLICACION_PENDIENTE"))
                .andExpect(jsonPath("$.duracionConvocatoriaMinutos").value(120))
                .andExpect(jsonPath("$.horaServidor").exists());
        assertThat(repo.findById(id).orElseThrow().getPublicacionIntentos()).isZero();
        verifyNoInteractions(bonita);
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "1.5", "2147483648", "null", "9223372036854775808"})
    void rechazaDuracionInvalida(String duracion) throws Exception {
        mvc.perform(post("/api/emergencias/{id}/lotes", id).contentType("application/json")
                .content("{\"duracionMinutos\":" + duracion + ",\"lotes\":[{\"tipoRecurso\":\"Agua\",\"cantidadRequerida\":1}]}"))
                .andExpect(status().isBadRequest());
        assertThat(repo.findById(id).orElseThrow().getEstado()).isEqualTo("REGISTRADA");
    }

    @ParameterizedTest @ValueSource(strings = {"[]", "[null]", "[{\"tipoRecurso\":\"\",\"cantidadRequerida\":1}]", "[{\"tipoRecurso\":\"Agua\",\"cantidadRequerida\":0}]"})
    void rechazaLotesInvalidos(String lotes) throws Exception {
        mvc.perform(post("/api/emergencias/{id}/lotes", id).contentType("application/json")
                .content("{\"duracionMinutos\":1,\"lotes\":" + lotes + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test void repeticionNoDuplicaLotesYCambioDevuelveConflicto() {
        servicio.publicarLotes(id, solicitud(1));
        assertThat(servicio.publicarLotes(id, solicitud(1)).getLotes()).hasSize(1);
        assertThatThrownBy(() -> servicio.publicarLotes(id, solicitud(2)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> assertThat(ex.getStatusCode().value()).isEqualTo(409));
    }

    @Test void publicaYConservaFechasEnRepeticiones() {
        servicio.publicarLotes(id, solicitud(120));
        worker.procesar(id);
        var original = servicio.obtenerPorId(id);
        assertThat(original.getEstado()).isEqualTo("CONVOCATORIA_ABIERTA");
        assertThat(original.getFechaVencimientoConvocatoria()).isEqualTo(original.getFechaAperturaConvocatoria().plusSeconds(7200));
        assertThat(servicio.publicarLotes(id, solicitud(120)).getFechaVencimientoConvocatoria())
                .isEqualTo(original.getFechaVencimientoConvocatoria());
        worker.procesar(id);
        verify(bonita, times(1)).confirmarPublicacion(10L, 20L);
    }

    @Test void falloYReinicioConservanPlazoYTarea() {
        servicio.publicarLotes(id, solicitud(1));
        doThrow(new IllegalStateException("Respuesta perdida")).doNothing().when(bonita).confirmarPublicacion(10L, 20L);
        assertThatThrownBy(() -> worker.procesar(id)).isInstanceOf(IllegalStateException.class);
        var pendiente = servicio.obtenerPorId(id);
        assertThat(pendiente.getEstado()).isEqualTo("PUBLICACION_PENDIENTE");
        assertThat(pendiente.getPublicacionError()).isNotBlank();
        assertThat(repo.findById(id).orElseThrow().getPublicacionTaskId()).isEqualTo(20L);
        new TransactionTemplate(tm).executeWithoutResult(s -> repo.bloquearPorId(id).orElseThrow().setPublicacionProximoIntento(Instant.EPOCH));
        new PublicacionConvocatoriaWorker(repo, bonita, tm).procesar(id);
        var abierta = servicio.obtenerPorId(id);
        assertThat(abierta.getEstado()).isEqualTo("CONVOCATORIA_ABIERTA");
        assertThat(abierta.getPublicacionIntentos()).isEqualTo(2);
        assertThat(abierta.getPublicacionError()).isNull();
        assertThat(abierta.getFechaVencimientoConvocatoria()).isEqualTo(pendiente.getFechaVencimientoConvocatoria());
        verify(bonita, times(1)).buscarTareaPublicacion(10L);
    }

    @Test void publicacionesSimultaneasCreanUnSoloLote() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch inicio = new CountDownLatch(1);
        try {
            Callable<Object> publicar = () -> { inicio.await(); return servicio.publicarLotes(id, solicitud(1)); };
            Future<?> a = pool.submit(publicar); Future<?> b = pool.submit(publicar);
            inicio.countDown(); a.get(10, TimeUnit.SECONDS); b.get(10, TimeUnit.SECONDS);
            assertThat(servicio.obtenerPorId(id).getLotes()).hasSize(1);
        } finally { pool.shutdownNow(); }
    }

    @Test void noAdmiteOfertasMientrasPublicacionPendiente() throws Exception {
        Long loteId = servicio.publicarLotes(id, solicitud(1)).getLotes().get(0).getId();
        mvc.perform(post("/api/ofertas").header("X-ONG-Username", "ong_prueba").contentType("application/json")
                .content("{\"emergenciaId\":" + id + ",\"loteId\":" + loteId + ",\"cantidadOfrecida\":1,\"unidad\":\"litros\",\"tiempoLlegada\":\"1 hora\"}"))
                .andExpect(status().isConflict());
    }
}
