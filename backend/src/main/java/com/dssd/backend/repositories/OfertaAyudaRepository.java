package com.dssd.backend.repositories;

import com.dssd.backend.models.OfertaAyuda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OfertaAyudaRepository extends JpaRepository<OfertaAyuda, Long> {
}
