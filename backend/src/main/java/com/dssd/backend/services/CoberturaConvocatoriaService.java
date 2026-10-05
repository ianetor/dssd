package com.dssd.backend.services;

import com.dssd.backend.dtos.CoberturaConvocatoriaDTO;
import com.dssd.backend.models.Emergencia;
import com.dssd.backend.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Transactional
public class CoberturaConvocatoriaService {
    private final EmergenciaRepository emergencias;
    private final LoteNecesidadRepository lotes;

    public void cerrarSiCompleta(Long id) {
        Emergencia e = bloquear(id);
        if (!"CONVOCATORIA_ABIERTA".equals(e.getEstado())) return;
        CoberturaConvocatoriaDTO cobertura = calcular(e);
        if (!cobertura.coberturaCompleta()) return;
        Instant ahora = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        e.setEstado("CONVOCATORIA_CERRADA");
        e.setMotivoCierre("COBERTURA_COMPLETA");
        e.setFechaCierreConvocatoria(ahora);
        e.setAvanceCoberturaEstado("PENDIENTE");
        e.setAvanceCoberturaIntentos(0);
        e.setAvanceCoberturaProximoIntento(ahora);
        e.setAvanceCoberturaError(null);
    }

    public CoberturaConvocatoriaDTO consultar(Long id) {
        // Coordinar con una oferta en curso: Bonita ve su commit, no una cobertura intermedia.
        return calcular(bloquear(id));
    }

    @Transactional(readOnly = true)
    public java.util.List<Long> buscarCompletasAbiertas() {
        return lotes.buscarEmergenciasCompletasAbiertas();
    }

    private Emergencia bloquear(Long id) {
        return emergencias.bloquearPorId(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Emergencia no encontrada"));
    }

    private CoberturaConvocatoriaDTO calcular(Emergencia e) {
        long total = lotes.countByEmergenciaId(e.getId());
        long faltantes = lotes.contarSinCubrir(e.getId());
        return new CoberturaConvocatoriaDTO(e.getId(), e.getCaseId(), total > 0 && faltantes == 0,
                total, faltantes, e.getEstado(), e.getMotivoCierre());
    }
}
