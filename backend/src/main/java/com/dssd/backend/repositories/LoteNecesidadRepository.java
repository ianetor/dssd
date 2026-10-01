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
}
