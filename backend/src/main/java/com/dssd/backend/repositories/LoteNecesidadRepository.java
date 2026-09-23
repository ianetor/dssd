package com.dssd.backend.repositories;

import com.dssd.backend.models.LoteNecesidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoteNecesidadRepository extends JpaRepository<LoteNecesidad, Long> {
    List<LoteNecesidad> findByEmergenciaId(Long emergenciaId);
}
