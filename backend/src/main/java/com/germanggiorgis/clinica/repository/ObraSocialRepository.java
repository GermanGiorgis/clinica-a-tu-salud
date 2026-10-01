package com.germanggiorgis.clinica.repository;

import com.germanggiorgis.clinica.model.ObraSocial;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ObraSocialRepository extends JpaRepository<ObraSocial, Long> {
    boolean existsByNombreIgnoreCase(String nombre);
    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
}
