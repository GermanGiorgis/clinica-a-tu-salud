package com.germanggiorgis.clinica.repository;

import com.germanggiorgis.clinica.model.EstadoTurno;
import com.germanggiorgis.clinica.model.Turno;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TurnoRepository extends JpaRepository<Turno, Long> {
    List<Turno> findByMedicoIdAndInicioBetween(Long medicoId, LocalDateTime desde, LocalDateTime hasta);
    List<Turno> findByMedicoIdAndInicioBetweenAndEstadoNot(
            Long medicoId, LocalDateTime desde, LocalDateTime hasta, EstadoTurno estadoExcluido);
    List<Turno> findByPacienteIdAndEstadoNotAndInicioBetween(
            Long pacienteId, EstadoTurno estadoExcluido, LocalDateTime desde, LocalDateTime hasta);
    List<Turno> findByPacienteIdOrderByInicioDesc(Long pacienteId);
    List<Turno> findByMedicoIdAndInicioBetweenOrderByInicioAsc(Long medicoId, LocalDateTime desde, LocalDateTime hasta);
    List<Turno> findByMedicoIdAndInicioGreaterThanEqualOrderByInicioAsc(Long medicoId, LocalDateTime desde);
    List<Turno> findByPacienteIdAndEstadoOrderByInicioDesc(Long pacienteId, EstadoTurno estado);
    boolean existsByMedicoIdAndPacienteId(Long medicoId, Long pacienteId);
}
