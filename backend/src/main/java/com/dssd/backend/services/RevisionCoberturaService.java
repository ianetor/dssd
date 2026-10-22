package com.dssd.backend.services;

import com.dssd.backend.dtos.*;
import com.dssd.backend.models.*;
import com.dssd.backend.repositories.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class RevisionCoberturaService {
    private final EmergenciaRepository emergencias;
    private final EvaluacionConvocatoriaRepository evaluaciones;
    private final DecisionCoberturaRepository decisiones;
    private final OfertaAyudaRepository ofertas;
    private final LoteNecesidadRepository lotes;
    private final DetalleOfertaRepository detalles;
    private final ObjectMapper mapper;
    private final BonitaRevisionClient bonita;
    private final SnapshotCoberturaService snapshots;
    private final ArchivoEvaluacionService archivo;
    private final TransactionTemplate tx;

    public RevisionCoberturaService(EmergenciaRepository emergencias, EvaluacionConvocatoriaRepository evaluaciones,
            DecisionCoberturaRepository decisiones, OfertaAyudaRepository ofertas, LoteNecesidadRepository lotes,
            DetalleOfertaRepository detalles, ObjectMapper mapper, BonitaRevisionClient bonita,
            SnapshotCoberturaService snapshots, ArchivoEvaluacionService archivo, PlatformTransactionManager tm) {
        this.emergencias = emergencias; this.evaluaciones = evaluaciones; this.decisiones = decisiones;
        this.ofertas = ofertas; this.lotes = lotes; this.detalles = detalles; this.mapper = mapper;
        this.bonita = bonita; this.snapshots = snapshots; this.tx = new TransactionTemplate(tm);
        this.archivo = archivo;
    }
    public record Resumen(Long emergenciaId, Long caseId, int ronda, String estado, String municipio,
            String zona, String gravedad, boolean compatible, String sincronizacion) {}
    public record Bandeja(List<Resumen> contenido, int pagina, int totalPaginas, long totalElementos) {}
    public record Aplicacion(String decisionId, int ronda, String estado, Instant fechaAperturaConvocatoria,
            Instant fechaVencimientoConvocatoria, Long fechaVencimientoEpochMillis) {}

    @Transactional(readOnly = true)
    public Bandeja listar(int pagina, int cantidad) {
        if (pagina < 0 || cantidad < 1 || cantidad > 100) throw fallo(HttpStatus.BAD_REQUEST, "Paginación inválida");
        var page = emergencias.buscarRevisiones(PageRequest.of(pagina, cantidad, Sort.by("id").descending()));
        var contenido = page.getContent().stream().map(e -> new Resumen(e.getId(), e.getCaseId(), e.rondaActual(),
                e.getEstado(), e.getMunicipioNombre(), e.getZonaAfectada(), e.getNivelGravedad(), compatible(e),
                decisiones.findByEmergenciaIdAndRonda(e.getId(), e.rondaActual()).map(DecisionCobertura::getEstado).orElse(null))).toList();
        return new Bandeja(contenido, page.getNumber(), page.getTotalPages(), page.getTotalElements());
    }
    @Transactional(readOnly = true)
    public RevisionCoberturaDTO detalle(Long id) {
        Emergencia e = emergencias.findById(id).orElseThrow(() -> fallo(HttpStatus.NOT_FOUND, "Emergencia no encontrada"));
        var historial = evaluaciones.findByEmergenciaIdOrderByRondaDesc(id).stream()
                .map(ev -> new RevisionCoberturaDTO.Historial(ev.getRonda(), ev.getFecha(), ev.isDepurada(), archivo.leer(ev))).toList();
        var ds = decisiones.findByEmergenciaIdOrderByRondaDesc(id).stream().map(this::respuesta).toList();
        RevisionCoberturaDTO.Snapshot snapshot = historial.isEmpty() ? historica(e) : historial.get(0).evaluacion();
        boolean puede = compatible(e) && "EVALUACION_INSUFICIENTE".equals(e.getEstado())
                && evaluaciones.findByEmergenciaIdAndRonda(id, e.rondaActual()).map(EvaluacionConvocatoria::isDepurada).orElse(false)
                && ds.stream().allMatch(d -> "CONFIRMADA".equals(d.estado()));
        return new RevisionCoberturaDTO(id, e.getCaseId(), e.rondaActual(), e.getEstado(), compatible(e), puede, snapshot, historial, ds);
    }
    private RevisionCoberturaDTO.Snapshot historica(Emergencia e) {
        if (e.getResultadoCoberturaJson() == null) throw fallo(HttpStatus.NOT_FOUND, "La emergencia no tiene una evaluación de cobertura");
        var resultado = leer(e.getResultadoCoberturaJson(), ValidacionCoberturaDTO.class);
        var perfiles = e.getValidacionesNacionalesJson() == null ? null : leer(e.getValidacionesNacionalesJson(), ValidacionCoberturaRequestDTO.class);
        Map<Long, ValidacionCoberturaRequestDTO.ValidacionOferta> mapa = new HashMap<>();
        if (perfiles != null) perfiles.validaciones().forEach(p -> mapa.put(p.ofertaId(), p));
        return snapshots.crear(e, lotes.findByEmergenciaId(e.getId()), ofertas.findByEmergenciaId(e.getId()).stream()
                .filter(o -> mapa.containsKey(o.getId())).toList(), mapa, resultado);
    }
    @Transactional
    public RevisionCoberturaDTO.Snapshot depurar(Long caseId, int ronda) {
        var referencia = emergencias.findByCaseId(caseId).orElseThrow(() -> fallo(HttpStatus.NOT_FOUND, "Caso no encontrado"));
        var e = emergencias.bloquearPorId(referencia.getId()).orElseThrow();
        var ev = evaluaciones.findByEmergenciaIdAndRonda(e.getId(), ronda)
                .orElseThrow(() -> fallo(HttpStatus.CONFLICT, "Primero debe completarse la evaluación de esta ronda"));
        var snapshot = archivo.leer(ev);
        if (snapshot.cobertura().coberturaSuficiente()) throw fallo(HttpStatus.CONFLICT, "No se depura la rama de cobertura suficiente");
        if (ev.isDepurada()) return snapshot;
        exigirCompatible(e);
        if (e.rondaActual() != ronda || !"CONVOCATORIA_CERRADA".equals(e.getEstado())) throw fallo(HttpStatus.CONFLICT, "La ronda ya no admite depuración");
        for (var item : snapshot.ofertas()) {
            var oferta = ofertas.findById(Long.valueOf(item.oferta().id())).orElseThrow();
            if (Boolean.TRUE.equals(oferta.getRecursosBloqueados())) throw fallo(HttpStatus.CONFLICT, "No se modifica una oferta con recursos comprometidos");
            oferta.setEstado(item.clasificacion());
            oferta.setMotivoExclusion(item.motivoExclusion());
            oferta.setRondaEvaluacion(ronda);
            if (item.perfil().nivelHabilitacion() != null) oferta.setNivelHabilitacion(item.perfil().nivelHabilitacion());
        }
        ofertas.flush();
        for (var lote : lotes.findByEmergenciaId(e.getId())) {
            lote.setCantidadCubierta((int)Math.min(lote.getCantidadRequerida(), detalles.cantidadOfertada(lote.getId())));
        }
        ev.setDepurada(true);
        e.setEstado("EVALUACION_INSUFICIENTE");
        if ("PENDIENTE".equals(e.getAvanceCoberturaEstado())) {
            e.setAvanceCoberturaEstado("CONFIRMADO"); e.setAvanceCoberturaError(null); e.setAvanceCoberturaProximoIntento(null);
        }
        return snapshot;
    }
    public RevisionCoberturaDTO.Decision decidir(Long id, DecisionCoberturaRequestDTO request, UsuarioResponseDTO usuario) {
        if (request.motivo() == null || request.motivo().isBlank() || request.motivo().length() > 1000 || request.decision() == null
                || request.ronda() == null || request.ronda() < 1) throw fallo(HttpStatus.BAD_REQUEST, "Decisión o motivo inválido");
        boolean reabrir = request.decision() == DecisionCoberturaRequestDTO.Accion.REABRIR;
        if (reabrir && (request.duracionMinutos() == null || request.duracionMinutos() < 1 || request.duracionMinutos() > 2147483647L)
                || !reabrir && request.duracionMinutos() != null) throw fallo(HttpStatus.BAD_REQUEST, "La nueva duración corresponde únicamente a REABRIR");
        var existente = tx.execute(s -> {
            var e = emergencias.bloquearPorId(id).orElseThrow(() -> fallo(HttpStatus.NOT_FOUND, "Emergencia no encontrada"));
            var previa = decisiones.findByEmergenciaIdAndRonda(id, request.ronda());
            if (previa.isPresent()) return mismaOConflicto(previa.get(), request, usuario);
            exigirPendiente(e, request.ronda()); return null;
        });
        if (existente != null) return existente;
        // Bonita fuera de la transacción: no retener el lock de emergencia durante HTTP.
        Long caseId = tx.execute(s -> emergencias.findById(id).orElseThrow().getCaseId());
        Long taskId;
        try { taskId = bonita.buscarTarea(caseId); }
        catch (RuntimeException ex) { throw fallo(HttpStatus.BAD_GATEWAY, "No se pudo comprobar la tarea en Bonita; la decisión no fue aceptada"); }
        if (taskId == null) throw fallo(HttpStatus.CONFLICT, "La tarea de evaluación todavía no está disponible en Bonita");
        return tx.execute(s -> {
            var e = emergencias.bloquearPorId(id).orElseThrow();
            var previa = decisiones.findByEmergenciaIdAndRonda(id, request.ronda());
            if (previa.isPresent()) return mismaOConflicto(previa.get(), request, usuario);
            exigirPendiente(e, request.ronda());
            var d = new DecisionCobertura();
            d.setId(UUID.randomUUID().toString()); d.setEmergencia(e); d.setRonda(request.ronda());
            d.setDecision(request.decision().name()); d.setMotivo(request.motivo().trim());
            d.setCoordinador(usuario.getUsername()); d.setCoordinadorId(usuario.getId()); d.setFecha(Instant.now());
            d.setDuracionMinutos(request.duracionMinutos()); d.setTareaBonitaId(taskId);
            d.setEstado("PENDIENTE"); d.setProximoIntento(Instant.now()); decisiones.save(d);
            e.setEstado(reabrir ? "REAPERTURA_PENDIENTE" : "CANCELACION_PENDIENTE");
            return respuesta(d);
        });
    }
    private RevisionCoberturaDTO.Decision mismaOConflicto(DecisionCobertura d, DecisionCoberturaRequestDTO r, UsuarioResponseDTO usuario) {
        if (!d.getDecision().equals(r.decision().name()) || !d.getMotivo().equals(r.motivo().trim())
                || !Objects.equals(d.getDuracionMinutos(), r.duracionMinutos()) || !Objects.equals(d.getCoordinadorId(), usuario.getId())
                || !Objects.equals(d.getCoordinador(), usuario.getUsername())) throw fallo(HttpStatus.CONFLICT, "Esta ronda ya tiene una decisión");
        return respuesta(d);
    }
    private void exigirPendiente(Emergencia e, int ronda) {
        exigirCompatible(e);
        if (ronda != e.rondaActual() || !"EVALUACION_INSUFICIENTE".equals(e.getEstado())) throw fallo(HttpStatus.CONFLICT, "La ronda no está pendiente de decisión");
        if (!evaluaciones.findByEmergenciaIdAndRonda(e.getId(), ronda).map(EvaluacionConvocatoria::isDepurada).orElse(false)) {
            throw fallo(HttpStatus.CONFLICT, "La depuración aún no finalizó");
        }
        if (decisiones.findByEmergenciaIdOrderByRondaDesc(e.getId()).stream().anyMatch(d -> !"CONFIRMADA".equals(d.getEstado()))) {
            throw fallo(HttpStatus.CONFLICT, "Hay una decisión anterior pendiente de sincronización");
        }
    }
    @Transactional
    public Aplicacion aplicar(String id) {
        var referencia = decisiones.findById(id).orElseThrow(() -> fallo(HttpStatus.NOT_FOUND, "Decisión no encontrada"));
        var e = emergencias.bloquearPorId(referencia.getEmergencia().getId()).orElseThrow();
        var d = decisiones.bloquear(id).orElseThrow();
        if (d.getAplicadaJson() != null) return leer(d.getAplicadaJson(), Aplicacion.class);
        exigirCompatible(e);
        if (e.rondaActual() != d.getRonda() || !Objects.equals(e.getEstado(),
                "REABRIR".equals(d.getDecision()) ? "REAPERTURA_PENDIENTE" : "CANCELACION_PENDIENTE")) {
            throw fallo(HttpStatus.CONFLICT, "La decisión pertenece a otra ronda o estado");
        }
        Aplicacion resultado;
        if ("REABRIR".equals(d.getDecision())) {
            Instant apertura = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            Instant vencimiento = apertura.plus(d.getDuracionMinutos(), ChronoUnit.MINUTES);
            e.setRondaConvocatoria(e.rondaActual() + 1); e.setDuracionConvocatoriaMinutos(d.getDuracionMinutos());
            e.setFechaAperturaConvocatoria(apertura); e.setFechaVencimientoConvocatoria(vencimiento);
            e.setFechaCierreConvocatoria(null); e.setMotivoCierre(null);
            e.setResultadoCoberturaJson(null); e.setValidacionesNacionalesJson(null);
            e.setAvanceCoberturaEstado(null); e.setAvanceCoberturaTaskId(null); e.setAvanceCoberturaIntentos(0);
            e.setAvanceCoberturaError(null); e.setAvanceCoberturaProximoIntento(null);
            e.setEstado("CONVOCATORIA_ABIERTA");
            resultado = new Aplicacion(id, e.rondaActual(), e.getEstado(), apertura, vencimiento, vencimiento.toEpochMilli());
        } else {
            e.setEstado("CANCELADA");
            resultado = new Aplicacion(id, e.rondaActual(), e.getEstado(), null, null, null);
        }
        d.setAplicadaJson(escribir(resultado)); d.setEstado("APLICADA"); d.setError(null);
        return resultado;
    }
    public RevisionCoberturaDTO.Decision respuesta(DecisionCobertura d) {
        return new RevisionCoberturaDTO.Decision(d.getId(), d.getRonda(), d.getDecision(), d.getMotivo(),
                d.getCoordinador(), d.getFecha(), d.getDuracionMinutos(), d.getEstado(), d.getIntentos(), d.getError());
    }
    public static boolean compatible(Emergencia e) { return "1.0".equals(e.getVersionFlujo()); }
    private static void exigirCompatible(Emergencia e) {
        if (!compatible(e)) throw fallo(HttpStatus.CONFLICT, "La revisión requiere una instancia nueva de RescueSync 1.0");
    }
    private <T> T leer(String json, Class<T> type) {
        try { return mapper.readValue(json, type); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("No se pudo leer la evaluación guardada", ex); }
    }
    private String escribir(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("No se pudo guardar el resultado", ex); }
    }
    private static ResponseStatusException fallo(HttpStatus status, String msg) { return new ResponseStatusException(status, msg); }
}
