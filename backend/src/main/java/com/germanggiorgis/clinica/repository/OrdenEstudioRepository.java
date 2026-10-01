package com.germanggiorgis.clinica.repository;

import com.germanggiorgis.clinica.model.OrdenEstudio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrdenEstudioRepository extends JpaRepository<OrdenEstudio, Long> {
    List<OrdenEstudio> findByConsultaId(Long consultaId);
}
