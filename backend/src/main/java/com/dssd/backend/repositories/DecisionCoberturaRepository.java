package com.dssd.backend.repositories;
import com.dssd.backend.models.DecisionCobertura;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
public interface DecisionCoberturaRepository extends JpaRepository<DecisionCobertura, String> {
    Optional<DecisionCobertura> findByEmergenciaIdAndRonda(Long id, Integer ronda);
    List<DecisionCobertura> findByEmergenciaIdOrderByRondaDesc(Long id);
    @Query("select d.id from DecisionCobertura d where d.estado <> 'CONFIRMADA' and d.proximoIntento <= :ahora order by d.fecha")
    List<String> buscarPendientes(@Param("ahora") Instant ahora);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DecisionCobertura d where d.id = :id")
    Optional<DecisionCobertura> bloquear(@Param("id") String id);
}
