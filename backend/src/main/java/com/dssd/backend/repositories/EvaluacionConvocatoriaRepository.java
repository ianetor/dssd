package com.dssd.backend.repositories;
import com.dssd.backend.models.EvaluacionConvocatoria;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface EvaluacionConvocatoriaRepository extends JpaRepository<EvaluacionConvocatoria, Long> {
    Optional<EvaluacionConvocatoria> findByEmergenciaIdAndRonda(Long id, Integer ronda);
    List<EvaluacionConvocatoria> findByEmergenciaIdOrderByRondaDesc(Long id);
}
