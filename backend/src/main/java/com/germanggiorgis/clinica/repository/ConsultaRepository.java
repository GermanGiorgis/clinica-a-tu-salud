package com.germanggiorgis.clinica.repository;

import com.germanggiorgis.clinica.model.Consulta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConsultaRepository extends JpaRepository<Consulta, Long> {
    Optional<Consulta> findByTurnoId(Long turnoId);
}
