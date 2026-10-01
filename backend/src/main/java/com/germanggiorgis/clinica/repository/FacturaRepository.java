package com.germanggiorgis.clinica.repository;

import com.germanggiorgis.clinica.model.Factura;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FacturaRepository extends JpaRepository<Factura, Long> {
    Optional<Factura> findByTurnoId(Long turnoId);
}
