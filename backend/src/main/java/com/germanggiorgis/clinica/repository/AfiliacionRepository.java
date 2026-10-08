package com.germanggiorgis.clinica.repository;

import com.germanggiorgis.clinica.model.Afiliacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AfiliacionRepository extends JpaRepository<Afiliacion, Long> {
    List<Afiliacion> findByPacienteIdOrderByVigenteDesdeDesc(Long pacienteId);
    boolean existsByPacienteIdAndObraSocialIdAndNumeroAfiliado(Long pacienteId, Long obraSocialId, String numeroAfiliado);
}
