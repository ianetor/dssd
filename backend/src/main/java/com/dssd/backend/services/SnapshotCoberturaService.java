package com.dssd.backend.services;

import com.dssd.backend.dtos.*;
import com.dssd.backend.dtos.EmergenciasDTO.EmergenciaResponseDTO;
import com.dssd.backend.dtos.ValidacionCoberturaRequestDTO.*;
import com.dssd.backend.models.*;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Component
public class SnapshotCoberturaService {
    public RevisionCoberturaDTO.Snapshot crear(Emergencia e, List<LoteNecesidad> lotes,
            Collection<OfertaAyuda> ofertas, Map<Long, ValidacionOferta> perfiles, ValidacionCoberturaDTO cobertura) {
        var builder = EmergenciaResponseDTO.builder().id(e.getId()).caseId(e.getCaseId())
                .nivelGravedad(e.getNivelGravedad()).zonaAfectada(e.getZonaAfectada()).descripcion(e.getDescripcion())
                .estado(e.getEstado()).municipioNombre(e.getMunicipioNombre())
                .duracionConvocatoriaMinutos(e.getDuracionConvocatoriaMinutos())
                .fechaAperturaConvocatoria(e.getFechaAperturaConvocatoria())
                .fechaVencimientoConvocatoria(e.getFechaVencimientoConvocatoria())
                .fechaCierreConvocatoria(e.getFechaCierreConvocatoria()).motivoCierre(e.getMotivoCierre());
        e.popularCamposEspecificos(builder);
        builder.lotes(lotes.stream().map(l -> LoteResponseDTO.builder().id(l.getId()).tipoRecurso(l.getTipoRecurso())
                .cantidadRequerida(l.getCantidadRequerida()).cantidadCubierta(l.getCantidadCubierta()).build()).toList());
        var detalles = ofertas.stream().sorted(Comparator.comparing(OfertaAyuda::getId)).map(o -> {
            if (o.getDetalles().size() != 1) throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esta revisión requiere ofertas de un único lote; revise la oferta " + o.getId());
            var d = o.getDetalles().get(0);
            Perfil perfil = perfiles.get(o.getId()).perfil();
            boolean habilitada = perfil.capacidades().stream().anyMatch(c -> c.rolPermitido() != RolPermitido.NO_HABILITADA);
            boolean utilizable = perfil.capacidades().stream().anyMatch(Capacidad::utilizable);
            String clasificacion = utilizable ? "Validada" : habilitada ? "SinDisponibilidad" : "NoHabilitada";
            String motivo = utilizable ? null : habilitada ? "Disponibilidad nacional insuficiente para la cantidad ofrecida"
                    : "La oferta no está habilitada por el Sistema Nacional";
            var oferta = new OfertaResponseDTO(o.getId().toString(), e.getId(), e.getNivelGravedad(),
                    d.getLote().getId(), d.getLote().getTipoRecurso(), d.getCantidadOfrecida(), o.getUnidad(),
                    o.getTiempoLlegada(), o.getObservaciones(), o.getFechaHora(), o.getEstado(), o.getOngLider());
            return new RevisionCoberturaDTO.OfertaEvaluada(oferta, perfil, clasificacion, motivo);
        }).toList();
        return new RevisionCoberturaDTO.Snapshot(builder.build(), cobertura, detalles);
    }
}
