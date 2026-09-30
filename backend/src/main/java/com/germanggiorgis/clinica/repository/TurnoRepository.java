package com.germanggiorgis.clinica.repository;

import com.germanggiorgis.clinica.model.Turno;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TurnoRepository extends JpaRepository<Turno, Long> {
    List<Turno> findByMedicoIdAndInicioBetween(Long medicoId, LocalDateTime desde, LocalDateTime hasta);
    List<Turno> findByPacienteIdOrderByInicioDesc(Long pacienteId);
}
