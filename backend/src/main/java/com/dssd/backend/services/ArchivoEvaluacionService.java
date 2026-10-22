package com.dssd.backend.services;

import com.dssd.backend.dtos.*;
import com.dssd.backend.dtos.EmergenciasDTO.EmergenciaResponseDTO;
import com.dssd.backend.dtos.ValidacionCoberturaRequestDTO.*;
import com.dssd.backend.models.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.util.*;

/** Traduce entre el histórico relacional y el contrato HTTP, sin persistir DTOs como JSON. */
@Service
public class ArchivoEvaluacionService {
    private final ObjectMapper mapper;
    public ArchivoEvaluacionService(ObjectMapper mapper) { this.mapper = mapper; }

    public void guardar(EvaluacionConvocatoria ev, RevisionCoberturaDTO.Snapshot snapshot) {
        if (ev.getUmbralPorcentaje() != null) throw new IllegalStateException("La evaluación ya está archivada");
        ev.setDatosEmergencia(mapper.convertValue(snapshot.emergencia(), EmergenciaEvaluada.class));
        ev.setUmbralPorcentaje(snapshot.cobertura().umbralPorcentaje());
        for (var dato : snapshot.cobertura().lotes()) {
            var lote = new LoteEvaluado();
            lote.setEvaluacion(ev); lote.setLoteOrigenId(dato.loteId()); lote.setTipoRecurso(dato.tipoRecurso());
            lote.setCantidadRequerida(dato.cantidadRequerida());
            lote.setCantidadPrincipal(dato.cantidadPrincipal()); lote.setCantidadApoyo(dato.cantidadApoyo());
            lote.setCantidadOfertadaAlCierre(snapshot.emergencia().getLotes().stream()
                    .filter(l -> l.getId().equals(dato.loteId())).mapToInt(LoteResponseDTO::getCantidadCubierta).findFirst().orElse(0));
            for (var item : snapshot.ofertas()) {
                var fuente = item.oferta();
                if (!fuente.loteId().equals(dato.loteId())) continue;
                var oferta = new OfertaEvaluada();
                oferta.setLote(lote); oferta.setOfertaOrigenId(Long.valueOf(fuente.id()));
                oferta.setCantidadOfrecida(fuente.cantidadOfrecida()); oferta.setUnidad(fuente.unidad());
                oferta.setTiempoLlegada(fuente.tiempoLlegada()); oferta.setObservaciones(fuente.observaciones());
                oferta.setFechaHora(fuente.fechaHora()); oferta.setEstadoAlCierre(fuente.estado()); oferta.setOngLider(fuente.ongLider());
                oferta.setClasificacion(item.clasificacion()); oferta.setMotivoExclusion(item.motivoExclusion());
                oferta.setResultadoNacional(item.perfil().resultado()); oferta.setOngNombre(item.perfil().ongNombre());
                oferta.setNivelHabilitacion(item.perfil().nivelHabilitacion());
                item.perfil().capacidades().forEach(c -> oferta.getCapacidades().add(mapper.convertValue(c, CapacidadEvaluada.class)));
                lote.getOfertas().add(oferta);
            }
            ev.getLotes().add(lote);
        }
        ev.setDatosJson(null); ev.setResultadoJson(null); ev.setValidacionesJson(null);
    }

    public RevisionCoberturaDTO.Snapshot leer(EvaluacionConvocatoria ev) {
        if (ev.getUmbralPorcentaje() == null) {
            try { return mapper.readValue(ev.getDatosJson(), RevisionCoberturaDTO.Snapshot.class); }
            catch (JsonProcessingException ex) { throw new IllegalStateException("No se pudo leer el histórico anterior", ex); }
        }
        var e = mapper.convertValue(ev.getDatosEmergencia(), EmergenciaResponseDTO.class);
        e.setId(ev.getEmergencia().getId());
        e.setLotes(ev.getLotes().stream().map(l -> LoteResponseDTO.builder().id(l.getLoteOrigenId())
                .tipoRecurso(l.getTipoRecurso()).cantidadRequerida(l.getCantidadRequerida())
                .cantidadCubierta(l.getCantidadOfertadaAlCierre()).build()).toList());
        var lotes = ev.getLotes().stream().map(l -> {
            long cantidad = l.getCantidadPrincipal() + l.getCantidadApoyo();
            return new ValidacionCoberturaDTO.LoteCobertura(l.getLoteOrigenId(), l.getTipoRecurso(), l.getCantidadRequerida(),
                    l.getCantidadPrincipal(), l.getCantidadApoyo(), cantidad,
                    l.getCantidadRequerida() == 0 ? 0 : 100.0 * cantidad / l.getCantidadRequerida(),
                    cantidad * 100 >= (long) l.getCantidadRequerida() * ev.getUmbralPorcentaje());
        }).toList();
        long cubiertos = lotes.stream().filter(ValidacionCoberturaDTO.LoteCobertura::coberturaMinimaAlcanzada).count();
        boolean suficiente = !lotes.isEmpty() && cubiertos == lotes.size();
        var cobertura = new ValidacionCoberturaDTO(e.getId(), e.getCaseId(),
                suficiente ? "COBERTURA_SUFICIENTE" : "COBERTURA_INSUFICIENTE", suficiente,
                ev.getUmbralPorcentaje(), lotes.size(), cubiertos, ev.getFecha(), lotes);
        var ofertas = ev.getLotes().stream().flatMap(l -> l.getOfertas().stream().map(o -> {
            var oferta = new OfertaResponseDTO(o.getOfertaOrigenId().toString(), e.getId(), e.getNivelGravedad(),
                    l.getLoteOrigenId(), l.getTipoRecurso(), o.getCantidadOfrecida(), o.getUnidad(), o.getTiempoLlegada(),
                    o.getObservaciones(), o.getFechaHora(), o.getEstadoAlCierre(), o.getOngLider());
            var perfil = new Perfil(o.getResultadoNacional(), o.getOngNombre(), o.getNivelHabilitacion(),
                    o.getCapacidades().stream().map(c -> mapper.convertValue(c, Capacidad.class)).toList());
            return new RevisionCoberturaDTO.OfertaEvaluada(oferta, perfil, o.getClasificacion(), o.getMotivoExclusion());
        })).toList();
        return new RevisionCoberturaDTO.Snapshot(e, cobertura, ofertas);
    }

    public ValidacionCoberturaRequestDTO entrada(EvaluacionConvocatoria ev) {
        return new ValidacionCoberturaRequestDTO(ev.getRonda(), leer(ev).ofertas().stream()
                .map(o -> new ValidacionOferta(Long.valueOf(o.oferta().id()), o.perfil())).toList());
    }
}
