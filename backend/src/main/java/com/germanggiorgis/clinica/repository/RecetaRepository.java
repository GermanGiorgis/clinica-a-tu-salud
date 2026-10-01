package com.germanggiorgis.clinica.repository;

import com.germanggiorgis.clinica.model.Receta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecetaRepository extends JpaRepository<Receta, Long> {
    List<Receta> findByConsultaId(Long consultaId);
}
