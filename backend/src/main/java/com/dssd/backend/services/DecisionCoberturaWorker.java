package com.dssd.backend.services;

import com.dssd.backend.repositories.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;

@Component
@ConditionalOnProperty(name = "cobertura.decision.worker-enabled", havingValue = "true", matchIfMissing = true)
public class DecisionCoberturaWorker {
    private final DecisionCoberturaRepository decisiones;
    private final EvaluacionConvocatoriaRepository evaluaciones;
    private final BonitaRevisionClient bonita;
    private final TransactionTemplate tx;
    private record Intento(Long emergenciaId, Long caseId, Long taskId, int ronda, String accion, String motivo, String coordinador) {}
    public DecisionCoberturaWorker(DecisionCoberturaRepository decisiones, EvaluacionConvocatoriaRepository evaluaciones,
            BonitaRevisionClient bonita, PlatformTransactionManager tm) {
        this.decisiones = decisiones; this.evaluaciones = evaluaciones; this.bonita = bonita; this.tx = new TransactionTemplate(tm);
    }
    @Scheduled(fixedDelayString = "${cobertura.decision.intervalo-ms:5000}")
    public void procesarPendientes() { for (String id : decisiones.buscarPendientes(Instant.now())) procesar(id); }
    public void procesar(String id) {
        var intento = tx.execute(s -> {
            var d = decisiones.bloquear(id).orElseThrow();
            if ("CONFIRMADA".equals(d.getEstado()) || d.getProximoIntento() != null && d.getProximoIntento().isAfter(Instant.now())) return null;
            d.setIntentos(d.getIntentos() + 1); d.setProximoIntento(Instant.now().plusSeconds(120));
            return new Intento(d.getEmergencia().getId(), d.getEmergencia().getCaseId(), d.getTareaBonitaId(), d.getRonda(), d.getDecision(), d.getMotivo(), d.getCoordinador());
        });
        if (intento == null) return;
        try {
            boolean aplicada = Boolean.TRUE.equals(tx.execute(s -> decisiones.findById(id).orElseThrow().getAplicadaJson() != null));
            if (!aplicada) {
                bonita.completar(intento.caseId(), intento.taskId(), id, intento.accion(), intento.motivo(), intento.coordinador());
                tx.executeWithoutResult(s -> {
                    var d = decisiones.bloquear(id).orElseThrow();
                    if ("PENDIENTE".equals(d.getEstado())) d.setEstado("ENVIADA");
                });
            }
            aplicada = Boolean.TRUE.equals(tx.execute(s -> decisiones.findById(id).orElseThrow().getAplicadaJson() != null));
            boolean rondaPosteriorEvaluada = "REABRIR".equals(intento.accion()) && Boolean.TRUE.equals(tx.execute(s ->
                    evaluaciones.findByEmergenciaIdAndRonda(intento.emergenciaId(), intento.ronda() + 1).isPresent()));
            boolean confirmada = aplicada && (rondaPosteriorEvaluada || bonita.avanceConfirmado(intento.caseId(), "CANCELAR".equals(intento.accion()), intento.ronda() + 1));
            tx.executeWithoutResult(s -> {
                var d = decisiones.bloquear(id).orElseThrow();
                if (confirmada) { d.setEstado("CONFIRMADA"); d.setProximoIntento(null); d.setError(null); }
                else { d.setProximoIntento(Instant.now().plusSeconds(5)); d.setError("Esperando que Bonita confirme el avance; no vuelva a enviar la decisión."); }
            });
        } catch (RuntimeException ex) {
            tx.executeWithoutResult(s -> {
                var d = decisiones.bloquear(id).orElseThrow();
                d.setError("No se pudo sincronizar con Bonita. Revise la cuenta técnica, el contrato y los conectores; se reintentará automáticamente.");
                d.setProximoIntento(Instant.now().plusSeconds(15));
            });
        }
    }
}
