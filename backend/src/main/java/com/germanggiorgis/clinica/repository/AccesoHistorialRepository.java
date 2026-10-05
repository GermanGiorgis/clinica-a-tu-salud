package com.germanggiorgis.clinica.repository;

import com.germanggiorgis.clinica.model.AccesoHistorial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccesoHistorialRepository extends JpaRepository<AccesoHistorial, Long> {
    List<AccesoHistorial> findTop200ByOrderByAccedidoEnDesc();
    List<AccesoHistorial> findTop200ByPacienteIdOrderByAccedidoEnDesc(Long pacienteId);
}
