package com.dssd.backend.repositories;

import com.dssd.backend.models.LoteNecesidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoteNecesidadRepository extends JpaRepository<LoteNecesidad, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select l from LoteNecesidad l where l.id = :id")
    java.util.Optional<LoteNecesidad> bloquearPorId(@org.springframework.data.repository.query.Param("id") Long id);
    List<LoteNecesidad> findByEmergenciaId(Long emergenciaId);
    long countByEmergenciaId(Long emergenciaId);

    @org.springframework.data.jpa.repository.Query("""
            select count(l) from LoteNecesidad l where l.emergencia.id = :id
            and (l.cantidadRequerida <= 0 or
                (select coalesce(sum(d.cantidadOfrecida), 0) from DetalleOferta d
                 where d.lote.id = l.id and d.oferta.estado in ('Registrada', 'Rectificada', 'Validada'))
                < l.cantidadRequerida)
            """)
    long contarSinCubrir(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Query("""
            select e.id from Emergencia e where e.estado = 'CONVOCATORIA_ABIERTA'
            and exists (select l.id from LoteNecesidad l where l.emergencia.id = e.id)
            and not exists (select l.id from LoteNecesidad l where l.emergencia.id = e.id
                and (l.cantidadRequerida <= 0 or
                    (select coalesce(sum(d.cantidadOfrecida), 0) from DetalleOferta d
                     where d.lote.id = l.id and d.oferta.estado in ('Registrada', 'Rectificada', 'Validada'))
                    < l.cantidadRequerida))
            order by e.id
            """)
    List<Long> buscarEmergenciasCompletasAbiertas();
}
