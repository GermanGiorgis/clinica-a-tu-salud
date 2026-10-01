package com.germanggiorgis.clinica.repository;

import com.germanggiorgis.clinica.model.Estudio;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EstudioRepository extends JpaRepository<Estudio, Long> {
    boolean existsByNombreIgnoreCase(String nombre);
    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
}
