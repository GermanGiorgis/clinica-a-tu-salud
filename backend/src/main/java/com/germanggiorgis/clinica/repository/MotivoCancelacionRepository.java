package com.germanggiorgis.clinica.repository;

import com.germanggiorgis.clinica.model.MotivoCancelacion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MotivoCancelacionRepository extends JpaRepository<MotivoCancelacion, Long> {
    boolean existsByNombreIgnoreCase(String nombre);
    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
}
