package com.germanggiorgis.clinica.repository;

import com.germanggiorgis.clinica.model.ResultadoEstudio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResultadoEstudioRepository extends JpaRepository<ResultadoEstudio, Long> {
    Optional<ResultadoEstudio> findByOrdenEstudioId(Long ordenEstudioId);
}
