package com.dssd.backend.services;

import com.dssd.backend.dtos.ValidacionCoberturaDTO;
import com.dssd.backend.dtos.ValidacionCoberturaRequestDTO;
import com.dssd.backend.dtos.ValidacionCoberturaRequestDTO.*;
import com.dssd.backend.models.*;
import com.dssd.backend.repositories.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class ValidacionCoberturaService {
    private final EmergenciaRepository emergencias;
    private final LoteNecesidadRepository lotes;
    private final OfertaAyudaRepository ofertas;
    private final ObjectMapper mapper;
    private final EvaluacionConvocatoriaRepository evaluaciones;
    private final SnapshotCoberturaService snapshots;
    private final ArchivoEvaluacionService archivo;

    public ValidacionCoberturaService(EmergenciaRepository emergencias, LoteNecesidadRepository lotes,
            OfertaAyudaRepository ofertas, ObjectMapper mapper, EvaluacionConvocatoriaRepository evaluaciones,
            SnapshotCoberturaService snapshots, ArchivoEvaluacionService archivo) {
        this.emergencias = emergencias;
        this.lotes = lotes;
        this.ofertas = ofertas;
        this.mapper = mapper;
        this.evaluaciones = evaluaciones;
        this.snapshots = snapshots;
        this.archivo = archivo;
    }

    public ValidacionCoberturaDTO validarPorCaso(Long caseId, ValidacionCoberturaRequestDTO request) {
        Emergencia referencia = emergencias.findByCaseId(caseId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Caso de emergencia no encontrado"));
        Emergencia emergencia = emergencias.bloquearPorId(referencia.getId()).orElseThrow();
        if (request.ronda() == null || request.ronda() != emergencia.rondaActual()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La evaluación pertenece a otra ronda");
        }
        var anterior = evaluaciones.findByEmergenciaIdAndRonda(emergencia.getId(), request.ronda());
        if (anterior.isPresent()) {
                var guardada = archivo.entrada(anterior.get());
                var entradas = request.validaciones().stream().collect(Collectors.toMap(ValidacionOferta::ofertaId, Function.identity(), (a,b) -> { throw invalido("Oferta duplicada"); }));
                var previas = guardada.validaciones().stream().collect(Collectors.toMap(ValidacionOferta::ofertaId, Function.identity()));
                if (!entradas.equals(previas)) throw new ResponseStatusException(HttpStatus.CONFLICT, "La ronda ya tiene una evaluación distinta");
                return archivo.leer(anterior.get()).cobertura();
        }
        // El timer de Bonita puede llegar antes que el worker local de cierre.
        if ("CONVOCATORIA_ABIERTA".equals(emergencia.getEstado())
                && emergencia.getFechaVencimientoConvocatoria() != null
                && !emergencia.getFechaVencimientoConvocatoria().isAfter(Instant.now())) {
            emergencia.setEstado("CONVOCATORIA_CERRADA");
            emergencia.setMotivoCierre("TIEMPO_AGOTADO");
            emergencia.setFechaCierreConvocatoria(emergencia.getFechaVencimientoConvocatoria());
        }
        if (!"CONVOCATORIA_CERRADA".equals(emergencia.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La convocatoria debe estar cerrada para validar cobertura");
        }

        List<LoteNecesidad> solicitados = lotes.findByEmergenciaId(emergencia.getId());
        Map<Long, OfertaAyuda> activas = ofertas.findByEmergenciaId(emergencia.getId()).stream()
                .filter(o -> List.of("Registrada", "Rectificada", "Validada").contains(o.getEstado()))
                .collect(Collectors.toMap(OfertaAyuda::getId, Function.identity()));
        Map<Long, ValidacionOferta> evaluaciones = new HashMap<>();
        for (ValidacionOferta validacion : request.validaciones()) {
            if (evaluaciones.putIfAbsent(validacion.ofertaId(), validacion) != null) {
                throw invalido("Una oferta no puede aparecer más de una vez");
            }
        }
        if (!evaluaciones.keySet().equals(activas.keySet())) {
            throw invalido("Se requiere exactamente una validación nacional por cada oferta activa de esta emergencia");
        }

        Map<Long, Long> principales = new HashMap<>();
        Map<Long, Long> apoyos = new HashMap<>();
        for (OfertaAyuda oferta : activas.values()) {
            // Una capacidad nacional representa el total solicitado a esa ONG para ese recurso.
            Map<String, List<DetalleOferta>> porRecurso = oferta.getDetalles().stream()
                    .collect(Collectors.groupingBy(d -> clave(NombreRecursoNacional.obtener(
                            d.getLote().getTipoRecurso(), oferta.getUnidad()))));
            List<Capacidad> capacidades = evaluaciones.get(oferta.getId()).perfil().capacidades();
            Set<String> vistas = new HashSet<>();
            for (Capacidad capacidad : capacidades) {
                String recurso = clave(NombreRecursoNacional.obtener(capacidad.tipoRecurso(), oferta.getUnidad()));
                if (!vistas.add(recurso) || !porRecurso.containsKey(recurso)) {
                    throw invalido("Las capacidades deben corresponder a los recursos de la oferta " + oferta.getId());
                }
                List<DetalleOferta> detalles = porRecurso.get(recurso);
                long cantidad = detalles.stream().mapToLong(DetalleOferta::getCantidadOfrecida).sum();
                if (detalles.stream().anyMatch(d -> !Objects.equals(d.getLote().getEmergencia().getId(), emergencia.getId()))
                        || cantidad != capacidad.cantidadSolicitada()) {
                    throw invalido("La validación nacional no corresponde a las cantidades de la oferta " + oferta.getId());
                }
                // La disponibilidad nacional es informativa: el bloqueo se realiza al adjudicar.
                // Una oferta sin disponibilidad suficiente debe rectificarse antes de poder contar.
                if (!capacidad.utilizable()) continue;
                Map<Long, Long> destino = capacidad.rolPermitido() == RolPermitido.PRINCIPAL ? principales : apoyos;
                for (DetalleOferta detalle : detalles) {
                    destino.merge(detalle.getLote().getId(), detalle.getCantidadOfrecida().longValue(), Long::sum);
                }
            }
            if (!vistas.equals(porRecurso.keySet())) throw invalido("Faltan capacidades nacionales de la oferta " + oferta.getId());
        }

        List<ValidacionCoberturaDTO.LoteCobertura> detalleLotes = solicitados.stream()
                .sorted(Comparator.comparing(LoteNecesidad::getId))
                .map(l -> {
                    long principal = principales.getOrDefault(l.getId(), 0L);
                    long apoyo = apoyos.getOrDefault(l.getId(), 0L);
                    long habilitada = principal + apoyo;
                    return new ValidacionCoberturaDTO.LoteCobertura(l.getId(), l.getTipoRecurso(),
                            l.getCantidadRequerida(), principal, apoyo, habilitada,
                            l.getCantidadRequerida() <= 0 ? 0 : 100.0 * habilitada / l.getCantidadRequerida(),
                            l.getCantidadRequerida() > 0 && habilitada * 2 >= l.getCantidadRequerida());
                }).toList();
        long cubiertos = detalleLotes.stream().filter(ValidacionCoberturaDTO.LoteCobertura::coberturaMinimaAlcanzada).count();
        int total = detalleLotes.size();
        boolean suficiente = total > 0 && cubiertos == total;
        ValidacionCoberturaDTO resultado = new ValidacionCoberturaDTO(emergencia.getId(), caseId,
                suficiente ? "COBERTURA_SUFICIENTE" : "COBERTURA_INSUFICIENTE", suficiente, 50,
                total, cubiertos, Instant.now(), detalleLotes);
        emergencia.setValidacionesNacionalesJson(serializar(request));
        emergencia.setResultadoCoberturaJson(serializar(resultado));
        EvaluacionConvocatoria evaluacion = new EvaluacionConvocatoria();
        evaluacion.setEmergencia(emergencia);
        evaluacion.setRonda(request.ronda());
        evaluacion.setFecha(resultado.fechaValidacion());
        archivo.guardar(evaluacion, snapshots.crear(emergencia, solicitados, activas.values(), evaluaciones, resultado));
        this.evaluaciones.save(evaluacion);
        return resultado;
    }

    @Transactional(readOnly = true)
    public ValidacionCoberturaDTO consultarPorCaso(Long caseId) {
        Emergencia emergencia = emergencias.findByCaseId(caseId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Caso de emergencia no encontrado"));
        if (emergencia.getResultadoCoberturaJson() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La cobertura aún no fue validada");
        }
        try {
            return mapper.readValue(emergencia.getResultadoCoberturaJson(), ValidacionCoberturaDTO.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo leer la validación de cobertura", ex);
        }
    }

    private String serializar(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("No se pudo guardar la validación de cobertura", ex); }
    }

    private static String clave(String value) {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static ResponseStatusException invalido(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }
}
