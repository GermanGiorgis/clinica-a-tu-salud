package com.germanggiorgis.clinica.repository;

import com.germanggiorgis.clinica.model.Encuesta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EncuestaRepository extends JpaRepository<Encuesta, Long> {
    Optional<Encuesta> findByTurnoId(Long turnoId);
}
