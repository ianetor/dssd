package com.dssd.backend.services;

import com.dssd.backend.models.Emergencia;
import com.dssd.backend.repositories.EmergenciaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** La propia emergencia contiene la operación durable; no depende de una cola en memoria. */
@Component
@ConditionalOnProperty(name = "convocatoria.publicacion.worker-enabled", havingValue = "true", matchIfMissing = true)
public class PublicacionConvocatoriaWorker {
    private final EmergenciaRepository emergencias;
    private final BonitaService bonita;
    private final TransactionTemplate tx;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PublicacionConvocatoriaWorker.class);

    public PublicacionConvocatoriaWorker(EmergenciaRepository emergencias, BonitaService bonita,
                                        PlatformTransactionManager transactionManager) {
        this.emergencias = emergencias;
        this.bonita = bonita;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Configuration
    @EnableScheduling
    static class Scheduling {}

    @Scheduled(fixedDelayString = "${convocatoria.publicacion.intervalo-ms:5000}")
    public void procesarPendientes() {
        for (Emergencia e : emergencias.findByEstado("PUBLICACION_PENDIENTE")) {
            try {
                procesar(e.getId());
            } catch (Exception ex) {
                // No registrar cuerpos HTTP: podrían contener cookies u otros datos sensibles.
                log.warn("Publicación pendiente emergencia={} caseId={} error={}", e.getId(), e.getCaseId(), ex.getClass().getSimpleName());
            }
        }
    }

    public void procesar(Long id) {
        Boolean preparado = tx.execute(status -> {
            Emergencia e = emergencias.bloquearPorId(id).orElseThrow();
            Instant ahora = Instant.now();
            if (!pendiente(e) || (e.getPublicacionProximoIntento() != null
                    && e.getPublicacionProximoIntento().isAfter(ahora))) return false;
            if (e.getFechaAperturaConvocatoria() == null) {
                e.setFechaAperturaConvocatoria(ahora);
                e.setFechaVencimientoConvocatoria(ahora.plus(e.getDuracionConvocatoriaMinutos(), ChronoUnit.MINUTES));
            }
            e.setPublicacionIntentos(e.getPublicacionIntentos() + 1);
            // Reserva el intento; tras un reinicio se recupera automáticamente.
            e.setPublicacionProximoIntento(ahora.plusSeconds(120));
            return true;
        });
        if (!Boolean.TRUE.equals(preparado)) return;
        try {
            // Persistir el ID ANTES del POST permite reconciliar una respuesta perdida.
            tx.executeWithoutResult(status -> {
                Emergencia e = emergencias.bloquearPorId(id).orElseThrow();
                if (pendiente(e) && e.getPublicacionTaskId() == null) {
                    e.setPublicacionTaskId(bonita.buscarTareaPublicacion(e.getCaseId()));
                }
            });
            tx.executeWithoutResult(status -> {
                Emergencia e = emergencias.bloquearPorId(id).orElseThrow();
                if (!pendiente(e)) return;
                bonita.confirmarPublicacion(e.getCaseId(), e.getPublicacionTaskId());
                e.setEstado("CONVOCATORIA_ABIERTA");
                e.setPublicacionError(null);
                e.setPublicacionProximoIntento(null);
            });
        } catch (Exception ex) {
            tx.executeWithoutResult(status -> {
                Emergencia e = emergencias.bloquearPorId(id).orElseThrow();
                if (!pendiente(e)) return;
                e.setPublicacionError("No se pudo confirmar la publicación en Bonita. Se reintentará automáticamente.");
                e.setPublicacionProximoIntento(Instant.now().plusSeconds(15));
            });
            throw ex;
        }
    }

    private boolean pendiente(Emergencia e) {
        return "PUBLICACION_PENDIENTE".equals(e.getEstado());
    }
}
