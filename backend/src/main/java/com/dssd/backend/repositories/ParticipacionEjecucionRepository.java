package com.dssd.backend.repositories;

import com.dssd.backend.models.ParticipacionEjecucion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParticipacionEjecucionRepository extends JpaRepository<ParticipacionEjecucion, Long> {
    List<ParticipacionEjecucion> findByEmergenciaIdOrderByOng(Long emergenciaId);

    Optional<ParticipacionEjecucion> findByEmergenciaIdAndOng(Long emergenciaId, String ong);

    List<ParticipacionEjecucion> findByOngOrderByFechaInicioDesc(String ong);
}
