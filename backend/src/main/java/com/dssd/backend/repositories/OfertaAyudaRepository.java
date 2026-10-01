package com.dssd.backend.repositories;

import com.dssd.backend.models.OfertaAyuda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OfertaAyudaRepository extends JpaRepository<OfertaAyuda, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from OfertaAyuda o where o.id = :id and o.ongLider = :ongLider")
    java.util.Optional<OfertaAyuda> bloquearPropia(
            @org.springframework.data.repository.query.Param("id") Long id,
            @org.springframework.data.repository.query.Param("ongLider") String ongLider);

    @org.springframework.data.jpa.repository.Query("select d.lote.emergencia.id from DetalleOferta d where d.oferta.id = :id and d.oferta.ongLider = :ongLider order by d.id")
    java.util.List<Long> emergenciaDeOferta(@org.springframework.data.repository.query.Param("id") Long id,
            @org.springframework.data.repository.query.Param("ongLider") String ongLider);

    java.util.List<OfertaAyuda> findByOngLiderOrderByFechaHoraDesc(String ongLider);
}
