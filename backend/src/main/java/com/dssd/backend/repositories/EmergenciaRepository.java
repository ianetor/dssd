package com.dssd.backend.repositories;

import com.dssd.backend.models.Emergencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmergenciaRepository extends JpaRepository<Emergencia, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from Emergencia e where e.id = :id")
    java.util.Optional<Emergencia> bloquearPorId(@org.springframework.data.repository.query.Param("id") Long id);

    List<Emergencia> findByEstado(String estado);
}
