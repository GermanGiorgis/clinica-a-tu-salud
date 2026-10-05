package com.germanggiorgis.clinica.repository;

import com.germanggiorgis.clinica.dto.ConteoMedicoEstado;
import com.germanggiorgis.clinica.dto.ConteoPorEstado;
import com.germanggiorgis.clinica.model.EstadoTurno;
import com.germanggiorgis.clinica.model.Turno;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface TurnoRepository extends JpaRepository<Turno, Long> {
    List<Turno> findByMedicoIdAndInicioBetween(Long medicoId, LocalDateTime desde, LocalDateTime hasta);
    List<Turno> findByMedicoIdAndInicioBetweenAndEstadoNot(
            Long medicoId, LocalDateTime desde, LocalDateTime hasta, EstadoTurno estadoExcluido);
    List<Turno> findByPacienteIdAndEstadoNotAndInicioBetween(
            Long pacienteId, EstadoTurno estadoExcluido, LocalDateTime desde, LocalDateTime hasta);
    @EntityGraph(attributePaths = {"medico.usuario", "paciente.usuario", "consultorio", "motivoCancelacion", "afiliacion.obraSocial"})
    List<Turno> findByPacienteIdOrderByInicioDesc(Long pacienteId);
    @EntityGraph(attributePaths = {"medico.usuario", "paciente.usuario", "consultorio", "motivoCancelacion", "afiliacion.obraSocial"})
    List<Turno> findByMedicoIdAndInicioBetweenOrderByInicioAsc(Long medicoId, LocalDateTime desde, LocalDateTime hasta);
    @EntityGraph(attributePaths = {"medico.usuario", "paciente.usuario", "consultorio", "motivoCancelacion", "afiliacion.obraSocial"})
    List<Turno> findByMedicoIdAndInicioGreaterThanEqualOrderByInicioAsc(Long medicoId, LocalDateTime desde);
    @EntityGraph(attributePaths = {"medico.usuario", "consultorio"})
    List<Turno> findByPacienteIdAndEstadoOrderByInicioDesc(Long pacienteId, EstadoTurno estado);
    /**
     * ¿Tiene este médico un turno vigente o ya atendido con este paciente que NO haya creado él mismo?
     * Es lo que lo habilita a ver el historial clínico completo (ver HistorialService).
     */
    boolean existsByMedicoIdAndPacienteIdAndEstadoInAndCreadoPorIdNot(
            Long medicoId, Long pacienteId, Collection<EstadoTurno> estados, Long creadoPorId);
    List<Turno> findByMedicoIdAndEstadoInAndInicioGreaterThanEqual(
            Long medicoId, Collection<EstadoTurno> estados, LocalDateTime desde);

    // Agregados para el panel de métricas: los calcula la base, no se cargan los turnos.
    @Query("select new com.germanggiorgis.clinica.dto.ConteoPorEstado(t.estado, count(t)) "
            + "from Turno t group by t.estado")
    List<ConteoPorEstado> contarPorEstado();

    @Query("select new com.germanggiorgis.clinica.dto.ConteoMedicoEstado(t.medico.id, t.estado, count(t)) "
            + "from Turno t group by t.medico.id, t.estado")
    List<ConteoMedicoEstado> contarPorMedicoYEstado();

    @Query("select count(distinct t.paciente.id) from Turno t where t.estado = :estado")
    long contarPacientesDistintosConEstado(@Param("estado") EstadoTurno estado);
}
