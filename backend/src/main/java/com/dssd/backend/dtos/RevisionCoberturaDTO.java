package com.dssd.backend.dtos;
import com.dssd.backend.dtos.EmergenciasDTO.EmergenciaResponseDTO;
import com.dssd.backend.dtos.ValidacionCoberturaRequestDTO.Perfil;
import java.time.Instant;
import java.util.List;

public record RevisionCoberturaDTO(Long emergenciaId, Long caseId, int ronda, String estado,
        boolean compatible, boolean puedeDecidir, Snapshot evaluacion,
        List<Historial> historial, List<Decision> decisiones) {
    public record OfertaEvaluada(OfertaResponseDTO oferta, Perfil perfil, String clasificacion, String motivoExclusion) {}
    public record Snapshot(EmergenciaResponseDTO emergencia, ValidacionCoberturaDTO cobertura,
            List<OfertaEvaluada> ofertas) {}
    public record Historial(int ronda, Instant fecha, boolean depurada, Snapshot evaluacion) {}
    public record Decision(String id, int ronda, String decision, String motivo, String coordinador,
            Instant fecha, Long duracionMinutos, String estado, int intentos, String error) {}
}
