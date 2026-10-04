package com.dssd.backend;

import com.dssd.backend.dtos.*;
import com.dssd.backend.models.*;
import com.dssd.backend.repositories.*;
import com.dssd.backend.services.CierreConvocatoriaWorker;
import com.dssd.backend.services.OfertaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(showSql = false)
@Import(OfertaService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CierreConvocatoriaStage3Test {
    @Autowired EmergenciaRepository emergencias;
    @Autowired LoteNecesidadRepository lotes;
    @Autowired OfertaAyudaRepository ofertas;
    @Autowired DetalleOfertaRepository detalles;
    @Autowired OfertaService servicio;
    @Autowired PlatformTransactionManager transactionManager;
    private TransactionTemplate tx;
    private Long emergenciaId;
    private Long loteId;
    private final UsuarioResponseDTO ong = UsuarioResponseDTO.builder().username("ong-prueba").build();

    @BeforeEach void preparar() {
        tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(s -> {
            detalles.deleteAll(); ofertas.deleteAll(); lotes.deleteAll(); emergencias.deleteAll();
            Incendio e = new Incendio();
            e.setNivelGravedad("ALTO"); e.setZonaAfectada("Norte");
            e.setDescripcion("Prueba de cierre"); e.setMunicipioNombre("Municipio");
            e.setEstado("CONVOCATORIA_ABIERTA");
            e.setFechaVencimientoConvocatoria(Instant.now().plusSeconds(3600));
            emergenciaId = emergencias.saveAndFlush(e).getId();
            LoteNecesidad lote = new LoteNecesidad(null, "Agua", 100, 0, e);
            loteId = lotes.saveAndFlush(lote).getId();
        });
    }

    private OfertaRequestDTO solicitud(int cantidad) {
        return new OfertaRequestDTO(emergenciaId, loteId, cantidad, "litros", "2 horas", "Prueba");
    }

    private void configurar(String estado, Instant vencimiento) {
        tx.executeWithoutResult(s -> {
            Emergencia e = emergencias.bloquearPorId(emergenciaId).orElseThrow();
            e.setEstado(estado); e.setFechaVencimientoConvocatoria(vencimiento);
        });
    }

    private void rechaza(Runnable operacion) {
        assertThatThrownBy(operacion::run).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test void workerRecuperaVencidasYPersisteCierreIdempotenteSinBonita() {
        Instant vencimiento = Instant.parse("2026-01-01T12:00:00Z");
        configurar("CONVOCATORIA_ABIERTA", vencimiento);
        tx.executeWithoutResult(s -> new CierreConvocatoriaWorker(emergencias).procesarVencidas());
        tx.executeWithoutResult(s -> new CierreConvocatoriaWorker(emergencias).procesarVencidas());
        Emergencia e = emergencias.findById(emergenciaId).orElseThrow();
        assertThat(e.getEstado()).isEqualTo("CONVOCATORIA_CERRADA");
        assertThat(e.getFechaCierreConvocatoria()).isEqualTo(vencimiento);
        assertThat(e.getMotivoCierre()).isEqualTo("TIEMPO_AGOTADO");
    }

    @Test void cierraEnElInstanteExactoDelVencimiento() {
        Instant vencimiento = Instant.parse("2026-01-01T12:00:00Z");
        configurar("CONVOCATORIA_ABIERTA", vencimiento);
        Integer antes = tx.execute(s -> emergencias.cerrarVencidas(vencimiento.minusMillis(1)));
        Integer alVencer = tx.execute(s -> emergencias.cerrarVencidas(vencimiento));
        assertThat(antes).isZero();
        assertThat(alVencer).isEqualTo(1);
    }

    @Test void workerNoCierraFuturasNiPublicacionesPendientes() {
        tx.executeWithoutResult(s -> new CierreConvocatoriaWorker(emergencias).procesarVencidas());
        assertThat(emergencias.findById(emergenciaId).orElseThrow().getEstado()).isEqualTo("CONVOCATORIA_ABIERTA");
        configurar("PUBLICACION_PENDIENTE", Instant.now().minusSeconds(60));
        tx.executeWithoutResult(s -> new CierreConvocatoriaWorker(emergencias).procesarVencidas());
        assertThat(emergencias.findById(emergenciaId).orElseThrow().getEstado()).isEqualTo("PUBLICACION_PENDIENTE");
    }

    @Test void admiteCrearRectificarYRetirarDentroDelPlazo() {
        OfertaResponseDTO creada = servicio.crear(ong, solicitud(20));
        Long id = Long.valueOf(creada.id());
        servicio.actualizar(ong, id, solicitud(30));
        assertThat(lotes.findById(loteId).orElseThrow().getCantidadCubierta()).isEqualTo(30);
        servicio.retirar(ong, id);
        assertThat(lotes.findById(loteId).orElseThrow().getCantidadCubierta()).isZero();
        assertThat(servicio.listar(ong, emergenciaId)).singleElement()
                .extracting(OfertaResponseDTO::estado).isEqualTo("Retirada");
    }

    @Test void rechazaCrearFueraDePlazoAntesDeQuePaseElWorker() {
        configurar("CONVOCATORIA_ABIERTA", Instant.now().minusSeconds(1));
        rechaza(() -> servicio.crear(ong, solicitud(20)));
        assertThat(ofertas.count()).isZero();
        assertThat(lotes.findById(loteId).orElseThrow().getCantidadCubierta()).isZero();
    }

    @Test void rechazaRectificarYRetirarFueraDePlazoSinAlterarOferta() {
        Long id = Long.valueOf(servicio.crear(ong, solicitud(20)).id());
        configurar("CONVOCATORIA_ABIERTA", Instant.now().minusSeconds(1));
        rechaza(() -> servicio.actualizar(ong, id, solicitud(30)));
        rechaza(() -> servicio.retirar(ong, id));
        assertThat(servicio.listar(ong, emergenciaId)).singleElement().satisfies(o -> {
            assertThat(o.cantidadOfrecida()).isEqualTo(20);
            assertThat(o.estado()).isEqualTo("Registrada");
        });
        assertThat(lotes.findById(loteId).orElseThrow().getCantidadCubierta()).isEqualTo(20);
    }

    @Test void cerradaConservaOfertasParaConsultaYRechazaTodaModificacion() {
        Long id = Long.valueOf(servicio.crear(ong, solicitud(20)).id());
        configurar("CONVOCATORIA_CERRADA", Instant.now().plusSeconds(3600));
        rechaza(() -> servicio.crear(ong, solicitud(10)));
        rechaza(() -> servicio.actualizar(ong, id, solicitud(30)));
        rechaza(() -> servicio.retirar(ong, id));
        assertThat(servicio.listar(ong, emergenciaId)).hasSize(1);
        var otraOng = UsuarioResponseDTO.builder().username("otra-ong").build();
        assertThat(servicio.listar(otraOng, emergenciaId)).isEmpty();
    }

    @Test void rechazaEstadosNoAbiertosYVencimientoSinConfigurar() {
        for (String estado : new String[]{"REGISTRADA", "PUBLICACION_PENDIENTE"}) {
            configurar(estado, Instant.now().plusSeconds(3600));
            rechaza(() -> servicio.crear(ong, solicitud(20)));
        }
        configurar("CONVOCATORIA_ABIERTA", null);
        rechaza(() -> servicio.crear(ong, solicitud(20)));
        assertThat(ofertas.count()).isZero();
    }
}
