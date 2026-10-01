package com.dssd.backend.services;

import com.dssd.backend.dtos.*;
import com.dssd.backend.models.*;
import com.dssd.backend.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@Transactional
@RequiredArgsConstructor
public class OfertaService {
    private final OfertaAyudaRepository ofertas;
    private final LoteNecesidadRepository lotes;
    private final DetalleOfertaRepository detalles;
    private String ong(UsuarioResponseDTO usuario) {
        // Bonita autentica al representante. No se mantiene un padrón local de usuarios.
        String username = usuario.getUsername();
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicie sesión nuevamente");
        }
        return username;
    }

    @Transactional(readOnly = true)
    public List<OfertaResponseDTO> listar(UsuarioResponseDTO usuario, Long emergenciaId) {
        return ofertas.findByOngLiderOrderByFechaHoraDesc(ong(usuario)).stream()
                .filter(o -> !o.getDetalles().isEmpty())
                .filter(o -> emergenciaId == null || Objects.equals(lote(o).getEmergencia().getId(), emergenciaId))
                .map(this::respuesta).toList();
    }

    public OfertaResponseDTO crear(UsuarioResponseDTO usuario, OfertaRequestDTO dto) {
        String ong = ong(usuario);
        LoteNecesidad lote = bloquear(dto.loteId());
        validar(lote, dto, 0);
        OfertaAyuda oferta = new OfertaAyuda();
        oferta.setOngLider(ong);
        oferta.setEstado("Registrada");
        DetalleOferta detalle = new DetalleOferta();
        detalle.setLote(lote);
        detalle.setOferta(oferta);
        oferta.getDetalles().add(detalle);
        aplicar(oferta, dto);
        ofertas.saveAndFlush(oferta);
        sincronizarCubierta(lote);
        return respuesta(oferta);
    }

    public OfertaResponseDTO actualizar(UsuarioResponseDTO usuario, Long id, OfertaRequestDTO dto) {
        OfertaAyuda oferta = propia(usuario, id);
        LoteNecesidad lote = bloquear(lote(oferta).getId());
        if (!Objects.equals(dto.loteId(), lote.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Para cambiar de lote retire la oferta y cree otra");
        }
        validar(lote, dto, oferta.getDetalles().get(0).getCantidadOfrecida());
        oferta.setEstado("Rectificada");
        aplicar(oferta, dto);
        ofertas.flush();
        sincronizarCubierta(lote);
        return respuesta(oferta);
    }

    public void retirar(UsuarioResponseDTO usuario, Long id) {
        OfertaAyuda oferta = propia(usuario, id);
        LoteNecesidad lote = bloquear(lote(oferta).getId());
        abierta(lote);
        oferta.setEstado("Retirada");
        oferta.setFechaHora(Instant.now());
        ofertas.flush();
        sincronizarCubierta(lote);
    }

    private OfertaAyuda propia(UsuarioResponseDTO usuario, Long id) {
        var emergenciaIds = ofertas.emergenciaDeOferta(id, ong(usuario));
        if (emergenciaIds.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Oferta no encontrada");
        OfertaAyuda oferta = ofertas.bloquearPropia(id, ong(usuario))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Oferta no encontrada"));
        if (!List.of("Registrada", "Rectificada").contains(oferta.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta oferta ya no admite modificaciones");
        }
        return oferta;
    }

    private LoteNecesidad bloquear(Long id) {
        return lotes.bloquearPorId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lote no encontrado"));
    }

    private LoteNecesidad lote(OfertaAyuda oferta) { return oferta.getDetalles().get(0).getLote(); }

    private void abierta(LoteNecesidad lote) {
        if ("PUBLICACION_PENDIENTE".equals(lote.getEmergencia().getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La convocatoria está pendiente de publicación");
        }
    }

    private void validar(LoteNecesidad lote, OfertaRequestDTO dto, int cantidadAnterior) {
        abierta(lote);
        if (!Objects.equals(lote.getEmergencia().getId(), dto.emergenciaId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El lote no pertenece a la emergencia");
        }
        long disponible = lote.getCantidadRequerida() - detalles.cantidadOfertada(lote.getId()) + cantidadAnterior;
        if (dto.cantidadOfrecida() > disponible) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La cantidad supera el faltante actual del lote: " + disponible);
        }
    }

    private void aplicar(OfertaAyuda oferta, OfertaRequestDTO dto) {
        oferta.getDetalles().get(0).setCantidadOfrecida(dto.cantidadOfrecida());
        oferta.setUnidad(dto.unidad());
        oferta.setTiempoLlegada(dto.tiempoLlegada());
        oferta.setObservaciones(dto.observaciones());
        oferta.setFechaHora(Instant.now());
    }

    /**
     * Recalcula y persiste cantidadCubierta en el lote sumando todas las ofertas
     * activas (Registrada, Rectificada, Validada) para ese lote.
     * Se llama después de flush para que la query de suma ya vea los nuevos datos.
     */
    private void sincronizarCubierta(LoteNecesidad lote) {
        long cubierta = detalles.cantidadOfertada(lote.getId());
        lote.setCantidadCubierta((int) Math.min(cubierta, lote.getCantidadRequerida()));
        lotes.save(lote);
    }

    private OfertaResponseDTO respuesta(OfertaAyuda o) {
        DetalleOferta d = o.getDetalles().get(0);
        return new OfertaResponseDTO(o.getId().toString(), d.getLote().getEmergencia().getId(),
                d.getLote().getId(), d.getLote().getTipoRecurso(), d.getCantidadOfrecida(), o.getUnidad(), o.getTiempoLlegada(),
                o.getObservaciones(), o.getFechaHora(), o.getEstado(), o.getOngLider());
    }

}
