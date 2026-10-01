package com.germanggiorgis.clinica.repository;

import com.germanggiorgis.clinica.model.Patologia;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatologiaRepository extends JpaRepository<Patologia, Long> {
    boolean existsByNombreIgnoreCase(String nombre);
    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
}
