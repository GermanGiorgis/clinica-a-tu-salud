package com.germanggiorgis.clinica.repository;

import com.germanggiorgis.clinica.model.Paciente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PacienteRepository extends JpaRepository<Paciente, Long> {
    Optional<Paciente> findByUsuarioId(Long usuarioId);
    /** El DNI solo identifica a UN paciente entre los verificados (ver V8). */
    Optional<Paciente> findByDniAndIdentidadVerificadaTrue(String dni);
    boolean existsByDniAndIdentidadVerificadaTrue(String dni);
    List<Paciente> findByIdentidadVerificadaFalseOrderByIdAsc();
}
