package com.germanggiorgis.clinica.dto;

import com.germanggiorgis.clinica.model.EstadoTurno;
import com.germanggiorgis.clinica.model.Turno;

import java.time.LocalDateTime;

public record TurnoResponse(
        Long id,
        Long medicoId,
        String medicoNombre,
        Long pacienteId,
        String pacienteNombre,
        Long consultorioId,
        String consultorioNombre,
        LocalDateTime inicio,
        LocalDateTime fin,
        EstadoTurno estado,
        String motivoConsulta,
        String motivoCancelacionNombre,
        String obraSocialNombre
) {
    public static TurnoResponse from(Turno turno) {
        var medicoUsuario = turno.getMedico().getUsuario();
        var pacienteUsuario = turno.getPaciente().getUsuario();
        return new TurnoResponse(
                turno.getId(),
                turno.getMedico().getId(),
                medicoUsuario.getNombre() + " " + medicoUsuario.getApellido(),
                turno.getPaciente().getId(),
                pacienteUsuario.getNombre() + " " + pacienteUsuario.getApellido(),
                turno.getConsultorio().getId(),
                turno.getConsultorio().getNombre(),
                turno.getInicio(),
                turno.getFin(),
                turno.getEstado(),
                turno.getMotivoConsulta(),
                turno.getMotivoCancelacion() != null ? turno.getMotivoCancelacion().getNombre() : null,
                turno.getAfiliacion() != null ? turno.getAfiliacion().getObraSocial().getNombre() : null
        );
    }
}
