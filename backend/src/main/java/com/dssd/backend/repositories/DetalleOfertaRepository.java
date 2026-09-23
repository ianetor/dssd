package com.dssd.backend.repositories;

import com.dssd.backend.models.DetalleOferta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DetalleOfertaRepository extends JpaRepository<DetalleOferta, Long> {
}
