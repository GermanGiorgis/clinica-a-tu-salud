package com.germanggiorgis.clinica.dto;

import com.germanggiorgis.clinica.model.HorarioAtencion;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

public record HorarioAtencionResponse(
        Long id,
        Long medicoId,
        String medicoNombre,
        Long consultorioId,
        String consultorioNombre,
        DayOfWeek diaSemana,
        LocalTime horaInicio,
        LocalTime horaFin,
        Integer duracionTurnoMin,
        LocalDate vigenteDesde,
        LocalDate vigenteHasta
) {
    public static HorarioAtencionResponse from(HorarioAtencion horario) {
        var medico = horario.getMedico();
        var usuario = medico.getUsuario();
        return new HorarioAtencionResponse(
                horario.getId(),
                medico.getId(),
                usuario.getNombre() + " " + usuario.getApellido(),
                horario.getConsultorio().getId(),
                horario.getConsultorio().getNombre(),
                horario.getDiaSemana(),
                horario.getHoraInicio(),
                horario.getHoraFin(),
                horario.getDuracionTurnoMin(),
                horario.getVigenteDesde(),
                horario.getVigenteHasta()
        );
    }
}
