package com.dssd.backend.services;

import com.dssd.backend.dtos.IndicadoresDTO;
import com.dssd.backend.models.DetalleOferta;
import com.dssd.backend.models.Emergencia;
import com.dssd.backend.models.OfertaAyuda;
import com.dssd.backend.repositories.OfertaAyudaRepository;
import com.dssd.backend.repositories.EmergenciaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.DiscriminatorValue;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Indicadores que combinan la base local (qué es cada emergencia) con la API de Bonita (cuándo pasó cada cosa).
 * El caseId guardado en Emergencia es la clave que une las dos fuentes.
 */
@Service
public class IndicadoresService {
    public static final String TAREA_REVISION = "Revisar Emergencia y Generar Lotes";
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(IndicadoresService.class);
    /** Bonita devuelve "2026-11-16 15:30:15.913" (milisegundos opcionales). */
    private static final DateTimeFormatter FECHA_BONITA = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss")
            .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd()
            .toFormatter();

    private static final List<String> OFERTAS_ACTIVAS = List.of("Registrada", "Rectificada", "Validada");
    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("yyyy-MM");

    private final EmergenciaRepository emergencias;
    private final OfertaAyudaRepository ofertas;
    private final BonitaService bonita;

    public IndicadoresService(EmergenciaRepository emergencias, OfertaAyudaRepository ofertas, BonitaService bonita) {
        this.emergencias = emergencias;
        this.ofertas = ofertas;
        this.bonita = bonita;
    }

    /** Indicadores de la base local: estado de las emergencias y suministros movilizados por mes. */
    @Transactional(readOnly = true)
    public IndicadoresDTO.Resumen resumen() {
        List<Emergencia> todas = emergencias.findAll();

        List<IndicadoresDTO.EstadoCantidad> estados = todas.stream()
                .collect(Collectors.groupingBy(Emergencia::getEstado, TreeMap::new, Collectors.counting()))
                .entrySet().stream().map(x -> new IndicadoresDTO.EstadoCantidad(x.getKey(), x.getValue())).toList();

        // Solo cuentan las emergencias que llegaron a ejecución: ahí los recursos quedaron comprometidos.
        // El mes es el del inicio de la ejecución; el orden es por mes y luego por recurso.
        Map<List<String>, Long> suministros = new TreeMap<>(Comparator.comparing((List<String> k) -> k.get(0))
                .thenComparing(k -> k.get(1)));
        for (Emergencia e : todas) {
            if (e.getFechaInicioEjecucion() == null) continue;
            String mes = MES.format(e.getFechaInicioEjecucion().atZone(ZoneId.systemDefault()));
            for (OfertaAyuda o : ofertas.findByEmergenciaId(e.getId())) {
                if (!OFERTAS_ACTIVAS.contains(o.getEstado())) continue;
                for (DetalleOferta d : o.getDetalles()) {
                    String recurso = NombreRecursoNacional.obtener(d.getLote().getTipoRecurso(), o.getUnidad());
                    String unidad = o.getUnidad() == null ? "" : o.getUnidad();
                    suministros.merge(List.of(mes, recurso, unidad), d.getCantidadOfrecida().longValue(), Long::sum);
                }
            }
        }
        List<IndicadoresDTO.SuministroMes> listaSuministros = suministros.entrySet().stream()
                .map(x -> new IndicadoresDTO.SuministroMes(x.getKey().get(0), x.getKey().get(1), x.getKey().get(2),
                        x.getValue()))
                .toList();
        return new IndicadoresDTO.Resumen(estados, listaSuministros);
    }

    /** Tiempo que tarda el Centro Coordinador en revisar la emergencia y publicar los lotes. */
    @Transactional(readOnly = true)
    public IndicadoresDTO.TiempoEtapa tiempoRevision() {
        List<IndicadoresDTO.Medicion> mediciones = new ArrayList<>();
        int sinDatos = 0;
        for (Emergencia e : emergencias.findAll()) {
            if (e.getCaseId() == null) continue;
            List<JsonNode> historial;
            try {
                historial = bonita.historialDelCaso(e.getCaseId());
            } catch (RuntimeException ex) {
                // Un caso borrado o inaccesible en Bonita no impide calcular el resto.
                log.warn("Sin historial de Bonita para caso={}: {}", e.getCaseId(), ex.getMessage());
                sinDatos++;
                continue;
            }
            List<IndicadoresDTO.Medicion> propias = medir(e, historial, TAREA_REVISION);
            if (propias.isEmpty()) sinDatos++;
            mediciones.addAll(propias);
        }
        mediciones.sort(Comparator.comparing(IndicadoresDTO.Medicion::fin));

        DoubleSummaryStatistics total = mediciones.stream()
                .mapToDouble(IndicadoresDTO.Medicion::segundos).summaryStatistics();
        List<IndicadoresDTO.PorTipo> porTipo = mediciones.stream()
                .collect(Collectors.groupingBy(IndicadoresDTO.Medicion::tipoEmergencia, TreeMap::new,
                        Collectors.summarizingDouble(IndicadoresDTO.Medicion::segundos)))
                .entrySet().stream()
                .map(t -> new IndicadoresDTO.PorTipo(t.getKey(), t.getValue().getAverage(), (int) t.getValue().getCount()))
                .toList();
        return new IndicadoresDTO.TiempoEtapa(TAREA_REVISION,
                mediciones.isEmpty() ? 0 : total.getAverage(),
                mediciones.isEmpty() ? 0 : total.getMin(),
                mediciones.isEmpty() ? 0 : total.getMax(),
                mediciones.size(), sinDatos, porTipo, mediciones);
    }

    /**
     * Bonita archiva una fila por cada estado que deja una tarea (initializing, ready, ... completed),
     * todas con el mismo sourceObjectId. La duración es desde la primera fila hasta la de estado completed
     * (o aborted, cuando la tarea la cierra un temporizador). Una reapertura genera otra instancia y se mide aparte.
     */
    private List<IndicadoresDTO.Medicion> medir(Emergencia e, List<JsonNode> historial, String tarea) {
        Map<String, List<JsonNode>> porInstancia = historial.stream()
                .filter(f -> tarea.equals(f.path("name").asText()))
                .collect(Collectors.groupingBy(f -> f.path("sourceObjectId").asText()));
        List<IndicadoresDTO.Medicion> resultado = new ArrayList<>();
        for (List<JsonNode> filas : porInstancia.values()) {
            Optional<LocalDateTime> fin = filas.stream()
                    .filter(f -> List.of("completed", "aborted").contains(f.path("state").asText()))
                    .map(IndicadoresService::fecha).filter(Objects::nonNull).max(Comparator.naturalOrder());
            Optional<LocalDateTime> inicio = filas.stream()
                    .map(IndicadoresService::fecha).filter(Objects::nonNull).min(Comparator.naturalOrder());
            if (fin.isEmpty() || inicio.isEmpty()) continue; // tarea todavía abierta
            double segundos = Duration.between(inicio.get(), fin.get()).toMillis() / 1000.0;
            resultado.add(new IndicadoresDTO.Medicion(e.getId(), e.getCaseId(), tipo(e), e.getMunicipioNombre(),
                    inicio.get(), fin.get(), segundos));
        }
        return resultado;
    }

    private static LocalDateTime fecha(JsonNode fila) {
        String texto = fila.path("archivedDate").asText("");
        if (texto.isBlank()) return null;
        try {
            return LocalDateTime.parse(texto, FECHA_BONITA);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String tipo(Emergencia e) {
        DiscriminatorValue d = Hibernate.getClass(e).getAnnotation(DiscriminatorValue.class);
        return d == null ? "OTRA" : d.value();
    }
}
