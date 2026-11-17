package com.dssd.backend.services;

import com.dssd.backend.dtos.EjecucionDTO;
import com.dssd.backend.dtos.UsuarioResponseDTO;
import com.dssd.backend.models.DetalleOferta;
import com.dssd.backend.models.Emergencia;
import com.dssd.backend.models.OfertaAyuda;
import com.dssd.backend.models.ParticipacionEjecucion;
import com.dssd.backend.repositories.EmergenciaRepository;
import com.dssd.backend.repositories.OfertaAyudaRepository;
import com.dssd.backend.repositories.ParticipacionEjecucionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Etapa de ejecución. Bonita avisa el inicio (Notificar a ONGs adjudicadas) y el cierre
 * (Cierre y liberacion de recursos); las ONGs y el Centro Coordinador operan desde la app web.
 */
@Service
public class EjecucionService {
    public static final String EN_EJECUCION = "EN_EJECUCION";
    public static final String CIERRE_PENDIENTE = "CIERRE_PENDIENTE";
    public static final String FINALIZADA = "FINALIZADA";
    private static final List<String> OFERTAS_ACTIVAS = List.of("Registrada", "Rectificada", "Validada");

    private final EmergenciaRepository emergencias;
    private final OfertaAyudaRepository ofertas;
    private final ParticipacionEjecucionRepository participaciones;
    private final BonitaService bonita;
    private final TransactionTemplate tx;

    public EjecucionService(EmergenciaRepository emergencias, OfertaAyudaRepository ofertas,
            ParticipacionEjecucionRepository participaciones, BonitaService bonita, PlatformTransactionManager tm) {
        this.emergencias = emergencias;
        this.ofertas = ofertas;
        this.participaciones = participaciones;
        this.bonita = bonita;
        this.tx = new TransactionTemplate(tm);
    }

    /** Bonita notifica la adjudicación: comienza la ejecución y cada ONG ve su notificación. Idempotente. */
    @Transactional
    public EjecucionDTO.Estado iniciar(Long caseId) {
        Emergencia e = bloquearPorCaso(caseId);
        if (iniciada(e)) return estado(e);
        if ("CANCELADA".equals(e.getEstado())) throw fallo(HttpStatus.CONFLICT, "La emergencia fue cancelada");
        List<OfertaAyuda> adjudicadas = adjudicadas(e.getId());
        if (adjudicadas.isEmpty()) throw fallo(HttpStatus.CONFLICT, "No hay ofertas adjudicadas para ejecutar");
        Instant ahora = Instant.now();
        adjudicadas.forEach(o -> o.setRecursosBloqueados(true));
        adjudicadas.stream().map(OfertaAyuda::getOngLider).filter(Objects::nonNull).distinct().sorted().forEach(ong -> {
            ParticipacionEjecucion p = new ParticipacionEjecucion();
            p.setEmergencia(e);
            p.setOng(ong);
            p.setFechaInicio(ahora);
            participaciones.save(p);
        });
        e.setEstado(EN_EJECUCION);
        e.setFechaInicioEjecucion(ahora);
        return estado(e);
    }

    /** Tablero de monitoreo del Centro Coordinador. */
    @Transactional(readOnly = true)
    public EjecucionDTO.Estado consultar(Long emergenciaId) {
        Emergencia e = emergencias.findById(emergenciaId)
                .orElseThrow(() -> fallo(HttpStatus.NOT_FOUND, "Emergencia no encontrada"));
        if (!iniciada(e)) throw fallo(HttpStatus.CONFLICT, "La emergencia todavía no está en ejecución");
        return estado(e);
    }

    /** Notificaciones de adjudicación de una ONG, con el estado de su participación. */
    @Transactional(readOnly = true)
    public List<EjecucionDTO.Adjudicacion> adjudicacionesDe(String ong) {
        return participaciones.findByOngOrderByFechaInicioDesc(ong).stream().map(p -> {
            Emergencia e = p.getEmergencia();
            return new EjecucionDTO.Adjudicacion(e.getId(), e.getEstado(), e.getZonaAfectada(), e.getNivelGravedad(),
                    e.getDescripcion(), respuesta(p, adjudicadas(e.getId())));
        }).toList();
    }

    /** La ONG marca sus actividades como finalizadas. Idempotente. */
    @Transactional
    public EjecucionDTO.Participacion finalizar(String ong, Long emergenciaId, String observaciones) {
        Emergencia e = emergencias.bloquearPorId(emergenciaId)
                .orElseThrow(() -> fallo(HttpStatus.NOT_FOUND, "Emergencia no encontrada"));
        ParticipacionEjecucion p = participaciones.findByEmergenciaIdAndOng(emergenciaId, ong)
                .orElseThrow(() -> fallo(HttpStatus.NOT_FOUND, "Su organización no participa de esta emergencia"));
        if (!p.finalizada()) {
            if (!EN_EJECUCION.equals(e.getEstado())) {
                throw fallo(HttpStatus.CONFLICT, "La emergencia ya no admite cambios de ejecución");
            }
            p.setEstado(ParticipacionEjecucion.FINALIZADA);
            p.setFechaFinalizacion(Instant.now());
            p.setObservaciones(observaciones == null || observaciones.isBlank() ? null : observaciones.trim());
        }
        return respuesta(p, adjudicadas(emergenciaId));
    }

    /**
     * El coordinador da por terminado el monitoreo: se completa la tarea de Bonita, que continúa con la
     * liberación nacional. Puede reintentarse: si Bonita falla, la emergencia queda en CIERRE_PENDIENTE.
     */
    public EjecucionDTO.Estado solicitarCierre(Long emergenciaId, EjecucionDTO.CierreRequest request,
            UsuarioResponseDTO usuario) {
        boolean forzar = request != null && Boolean.TRUE.equals(request.forzar());
        String motivo = request == null || request.motivo() == null ? "" : request.motivo().trim();
        Long caseId = tx.execute(s -> {
            Emergencia e = emergencias.bloquearPorId(emergenciaId)
                    .orElseThrow(() -> fallo(HttpStatus.NOT_FOUND, "Emergencia no encontrada"));
            if (FINALIZADA.equals(e.getEstado())) return null;
            if (EN_EJECUCION.equals(e.getEstado())) {
                validarCierre(participaciones.findByEmergenciaIdOrderByOng(emergenciaId), forzar, motivo);
                e.setEstado(CIERRE_PENDIENTE);
                e.setCoordinadorCierre(usuario.getUsername());
                e.setMotivoCierreEjecucion(motivo.isEmpty() ? null : motivo);
            } else if (!CIERRE_PENDIENTE.equals(e.getEstado())) {
                throw fallo(HttpStatus.CONFLICT, "La emergencia no está en ejecución");
            }
            return e.getCaseId();
        });
        if (caseId != null) {
            // Bonita fuera de la transacción: no retener el lock de emergencia durante HTTP.
            try {
                bonita.completarMonitoreo(caseId);
            } catch (IllegalStateException ex) {
                throw fallo(HttpStatus.CONFLICT, ex.getMessage());
            } catch (RuntimeException ex) {
                throw fallo(HttpStatus.BAD_GATEWAY,
                        "No se pudo avanzar el caso en Bonita. El cierre quedó pendiente: vuelva a intentarlo.");
            }
        }
        return tx.execute(s -> estado(emergencias.findById(emergenciaId).orElseThrow()));
    }


    private void validarCierre(List<ParticipacionEjecucion> lista, boolean forzar, String motivo) {
        if (lista.isEmpty()) {
            throw fallo(HttpStatus.CONFLICT, "No hay ONGs participando de la ejecución");
        }

        long pendientes = lista.stream().filter(p -> !p.finalizada()).count();
        if (pendientes == 0) {
            return; // todas finalizaron: se puede cerrar sin forzar
        }

        if (!forzar) {
            throw fallo(HttpStatus.CONFLICT, "Quedan " + pendientes
                    + " ONGs en curso. Para cerrar igual, fuerce el cierre con un motivo");
        }
        if (motivo.isBlank()) {
            throw fallo(HttpStatus.CONFLICT, "El cierre forzado requiere un motivo");
        }
    }

    /** Bonita confirma que el Sistema Nacional liberó los recursos: la emergencia queda finalizada. Idempotente. */
    @Transactional
    public EjecucionDTO.Estado cerrar(Long caseId) {
        Emergencia e = bloquearPorCaso(caseId);
        if (FINALIZADA.equals(e.getEstado())) return estado(e);
        if (!EN_EJECUCION.equals(e.getEstado()) && !CIERRE_PENDIENTE.equals(e.getEstado())) {
            throw fallo(HttpStatus.CONFLICT, "La emergencia no está en ejecución");
        }
        ofertas.findByEmergenciaId(e.getId()).forEach(o -> o.setRecursosBloqueados(false));
        e.setEstado(FINALIZADA);
        e.setFechaCierreEjecucion(Instant.now());
        return estado(e);
    }

    private Emergencia bloquearPorCaso(Long caseId) {
        Emergencia referencia = emergencias.findByCaseId(caseId)
                .orElseThrow(() -> fallo(HttpStatus.NOT_FOUND, "Caso de emergencia no encontrado"));
        return emergencias.bloquearPorId(referencia.getId()).orElseThrow();
    }

    private static boolean iniciada(Emergencia e) {
        return List.of(EN_EJECUCION, CIERRE_PENDIENTE, FINALIZADA).contains(e.getEstado());
    }

    private List<OfertaAyuda> adjudicadas(Long emergenciaId) {
        return ofertas.findByEmergenciaId(emergenciaId).stream()
                .filter(o -> OFERTAS_ACTIVAS.contains(o.getEstado())).toList();
    }

    private EjecucionDTO.Estado estado(Emergencia e) {
        List<OfertaAyuda> adjudicadas = adjudicadas(e.getId());
        List<EjecucionDTO.Participacion> lista = participaciones.findByEmergenciaIdOrderByOng(e.getId()).stream()
                .map(p -> respuesta(p, adjudicadas)).toList();
        long finalizadas = lista.stream().filter(p -> ParticipacionEjecucion.FINALIZADA.equals(p.estado())).count();
        boolean todas = !lista.isEmpty() && finalizadas == lista.size();
        return new EjecucionDTO.Estado(e.getId(), e.getCaseId(), e.getEstado(), e.getZonaAfectada(),
                e.getNivelGravedad(), e.getFechaInicioEjecucion(), e.getFechaCierreEjecucion(),
                e.getCoordinadorCierre(), e.getMotivoCierreEjecucion(), lista.size(), finalizadas,
                EN_EJECUCION.equals(e.getEstado()) && todas, lista);
    }

    private EjecucionDTO.Participacion respuesta(ParticipacionEjecucion p, List<OfertaAyuda> adjudicadas) {
        // Mismo agrupamiento que el compromiso nacional: total por recurso de la ONG.
        Map<String, EjecucionDTO.Recurso> recursos = new LinkedHashMap<>();
        for (OfertaAyuda oferta : adjudicadas) {
            if (!p.getOng().equals(oferta.getOngLider())) continue;
            for (DetalleOferta d : oferta.getDetalles()) {
                String tipo = NombreRecursoNacional.obtener(d.getLote().getTipoRecurso(), oferta.getUnidad());
                recursos.merge(tipo, new EjecucionDTO.Recurso(tipo, d.getCantidadOfrecida(), oferta.getUnidad()),
                        (a, b) -> new EjecucionDTO.Recurso(tipo, a.cantidad() + b.cantidad(), a.unidad()));
            }
        }
        return new EjecucionDTO.Participacion(p.getOng(), p.getEstado(), p.getFechaInicio(), p.getFechaFinalizacion(),
                p.getObservaciones(), recursos.values().stream()
                        .sorted(Comparator.comparing(EjecucionDTO.Recurso::tipoRecurso)).collect(Collectors.toList()));
    }

    private static ResponseStatusException fallo(HttpStatus status, String mensaje) {
        return new ResponseStatusException(status, mensaje);
    }
}
