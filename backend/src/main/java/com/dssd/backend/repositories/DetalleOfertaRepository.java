package com.dssd.backend.repositories;

import com.dssd.backend.models.DetalleOferta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DetalleOfertaRepository extends JpaRepository<DetalleOferta, Long> {
    @org.springframework.data.jpa.repository.Query("select coalesce(sum(d.cantidadOfrecida), 0) from DetalleOferta d where d.lote.id = :loteId and d.oferta.estado in ('Registrada', 'Rectificada', 'Validada')")
    long cantidadOfertada(@org.springframework.data.repository.query.Param("loteId") Long loteId);
}
