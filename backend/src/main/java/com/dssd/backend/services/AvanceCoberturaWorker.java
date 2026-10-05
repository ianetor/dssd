package com.dssd.backend.services;

import com.dssd.backend.models.Emergencia;
import com.dssd.backend.repositories.EmergenciaRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;

@Component
public class AvanceCoberturaWorker {
    private final EmergenciaRepository emergencias;
    private final CoberturaConvocatoriaService cobertura;
    private final BonitaService bonita;
    private final TransactionTemplate tx;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AvanceCoberturaWorker.class);

    public AvanceCoberturaWorker(EmergenciaRepository emergencias, CoberturaConvocatoriaService cobertura,
            BonitaService bonita, PlatformTransactionManager tm) {
        this.emergencias = emergencias; this.cobertura = cobertura; this.bonita = bonita;
        this.tx = new TransactionTemplate(tm);
    }

    @Scheduled(fixedDelayString = "${convocatoria.cobertura.intervalo-ms:5000}")
    public void procesarPendientes() {
        for (Long id : cobertura.buscarCompletasAbiertas()) cobertura.cerrarSiCompleta(id);
        for (Emergencia e : emergencias.findByAvanceCoberturaEstadoAndAvanceCoberturaProximoIntentoLessThanEqual("PENDIENTE", Instant.now())) {
            try { procesar(e.getId()); }
            catch (Exception ex) { log.warn("Avance por cobertura pendiente emergencia={} error={}", e.getId(), ex.getClass().getSimpleName()); }
        }
    }

    private record Intento(Long caseId, Long taskId) {}

    public void procesar(Long id) {
        Intento intento = tx.execute(s -> {
            Emergencia e = emergencias.bloquearPorId(id).orElseThrow();
            if (!pendiente(e) || (e.getAvanceCoberturaProximoIntento() != null
                    && e.getAvanceCoberturaProximoIntento().isAfter(Instant.now()))) return null;
            e.setAvanceCoberturaIntentos((e.getAvanceCoberturaIntentos() == null ? 0 : e.getAvanceCoberturaIntentos()) + 1);
            e.setAvanceCoberturaProximoIntento(Instant.now().plusSeconds(120));
            return new Intento(e.getCaseId(), e.getAvanceCoberturaTaskId());
        });
        if (intento == null) return;
        try {
            // Ninguna llamada a Bonita se hace con locks de BD retenidos:
            // la evaluación consulta la cobertura en este backend.
            if (!bonita.adjudicacionDisponible(intento.caseId())) {
                Long taskId = intento.taskId() != null ? intento.taskId() : bonita.buscarTareaOfertas(intento.caseId());
                tx.executeWithoutResult(s -> emergencias.bloquearPorId(id).orElseThrow().setAvanceCoberturaTaskId(taskId));
                bonita.completarRecepcionPorCobertura(intento.caseId(), taskId);
                if (!bonita.adjudicacionDisponible(intento.caseId())) {
                    throw new IllegalStateException("Realizar Ofertas finalizó; esperando Adjudicar Ofertas. Si no aparece, revisá la evaluación en Bonita.");
                }
            }
            tx.executeWithoutResult(s -> {
                Emergencia e = emergencias.bloquearPorId(id).orElseThrow();
                if (!pendiente(e)) return;
                e.setAvanceCoberturaEstado("CONFIRMADO");
                e.setAvanceCoberturaError(null); e.setAvanceCoberturaProximoIntento(null);
            });
        } catch (Exception ex) {
            tx.executeWithoutResult(s -> {
                Emergencia e = emergencias.bloquearPorId(id).orElseThrow();
                if (!pendiente(e)) return;
                e.setAvanceCoberturaError(ex instanceof IllegalStateException ? ex.getMessage()
                        : "No se pudo confirmar el avance en Bonita. Se reintentará automáticamente.");
                e.setAvanceCoberturaProximoIntento(Instant.now().plusSeconds(15));
            });
            throw ex;
        }
    }

    private boolean pendiente(Emergencia e) {
        return "CONVOCATORIA_CERRADA".equals(e.getEstado()) && "COBERTURA_COMPLETA".equals(e.getMotivoCierre())
                && "PENDIENTE".equals(e.getAvanceCoberturaEstado());
    }
}
