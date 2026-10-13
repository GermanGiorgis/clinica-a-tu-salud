package com.germanggiorgis.clinica.repository;

import com.germanggiorgis.clinica.model.Patologia;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PatologiaRepository extends JpaRepository<Patologia, Long> {

    /** Con sus especialidades en la misma consulta: el listado público las muestra todas. */
    @Override
    @EntityGraph(attributePaths = "especialidades")
    List<Patologia> findAll();

    boolean existsByNombreIgnoreCase(String nombre);
    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
}
