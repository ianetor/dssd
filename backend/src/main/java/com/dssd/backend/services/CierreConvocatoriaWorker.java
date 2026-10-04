package com.dssd.backend.services;

import com.dssd.backend.repositories.EmergenciaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

/** El plazo persistido cierra la recepción incluso cuando Bonita está desconectado. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "convocatoria.cierre.worker-enabled", havingValue = "true", matchIfMissing = true)
public class CierreConvocatoriaWorker {
    private final EmergenciaRepository emergencias;

    @Scheduled(fixedDelayString = "${convocatoria.cierre.intervalo-ms:5000}")
    @Transactional
    public void procesarVencidas() {
        emergencias.cerrarVencidas(Instant.now());
    }
}
