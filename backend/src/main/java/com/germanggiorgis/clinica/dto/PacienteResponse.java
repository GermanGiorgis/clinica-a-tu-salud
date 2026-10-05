package com.germanggiorgis.clinica.dto;

import com.germanggiorgis.clinica.model.Paciente;

import java.time.LocalDate;

/** Lo que ve un admin para verificar la identidad de un paciente contra su documento. */
public record PacienteResponse(
        Long id,
        Long usuarioId,
        String nombre,
        String apellido,
        String email,
        String telefono,
        String dni,
        LocalDate fechaNacimiento,
        boolean identidadVerificada,
        boolean cuentaActiva
) {
    public static PacienteResponse from(Paciente paciente) {
        var usuario = paciente.getUsuario();
        return new PacienteResponse(
                paciente.getId(),
                usuario.getId(),
                usuario.getNombre(),
                usuario.getApellido(),
                usuario.getEmail(),
                usuario.getTelefono(),
                paciente.getDni(),
                paciente.getFechaNacimiento(),
                paciente.isIdentidadVerificada(),
                usuario.isActivo());
    }
}
