package com.germanggiorgis.clinica.repository;

import com.germanggiorgis.clinica.model.HorarioAtencion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;

public interface HorarioAtencionRepository extends JpaRepository<HorarioAtencion, Long> {
    List<HorarioAtencion> findByMedicoIdAndDiaSemana(Long medicoId, DayOfWeek diaSemana);
    List<HorarioAtencion> findByMedicoId(Long medicoId);
}
