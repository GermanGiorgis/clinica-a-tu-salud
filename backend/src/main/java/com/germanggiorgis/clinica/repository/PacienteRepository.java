package com.germanggiorgis.clinica.repository;

import com.germanggiorgis.clinica.model.Paciente;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PacienteRepository extends JpaRepository<Paciente, Long> {
    Optional<Paciente> findByUsuarioId(Long usuarioId);
    /** El DNI solo identifica a UN paciente entre los verificados (ver V8). */
    Optional<Paciente> findByDniAndIdentidadVerificadaTrue(String dni);
    boolean existsByDniAndIdentidadVerificadaTrue(String dni);
    List<Paciente> findByIdentidadVerificadaFalseOrderByIdAsc();

    /**
     * Toma el candado de la fila del paciente hasta el fin de la transacción. Sirve para serializar
     * sus reservas simultáneas: sin esto, dos pedidos a la vez cuentan "1 turno pendiente" y los dos
     * reservan, pasándose del tope (ver TurnoService.crear).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Paciente p where p.id = :id")
    Optional<Paciente> bloquearPorId(@Param("id") Long id);
}
