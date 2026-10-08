package com.germanggiorgis.clinica.dto;

import com.germanggiorgis.clinica.model.EstadoTurno;
import com.germanggiorgis.clinica.model.Turno;

import java.time.LocalDateTime;

/**
 * Un turno vigente que cae dentro de un feriado o una ausencia. Trae el teléfono del paciente
 * porque el sistema no envía avisos: es quien está en el mostrador el que tiene que llamar.
 */
public record TurnoAfectadoResponse(
        Long id,
        LocalDateTime inicio,
        EstadoTurno estado,
        Long medicoId,
        String medicoNombre,
        String pacienteNombre,
        String pacienteTelefono,
        String consultorioNombre
) {
    public static TurnoAfectadoResponse from(Turno turno) {
        var medico = turno.getMedico().getUsuario();
        var paciente = turno.getPaciente().getUsuario();
        return new TurnoAfectadoResponse(
                turno.getId(),
                turno.getInicio(),
                turno.getEstado(),
                turno.getMedico().getId(),
                medico.getNombre() + " " + medico.getApellido(),
                paciente.getNombre() + " " + paciente.getApellido(),
                paciente.getTelefono(),
                turno.getConsultorio().getNombre());
    }
}
