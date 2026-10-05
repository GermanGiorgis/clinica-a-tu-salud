package com.germanggiorgis.clinica.dto;

import com.germanggiorgis.clinica.model.AccesoHistorial;
import com.germanggiorgis.clinica.model.Rol;

import java.time.LocalDateTime;

public record AccesoHistorialResponse(
        Long id,
        Long usuarioId,
        String usuarioEmail,
        String usuarioNombre,
        Rol usuarioRol,
        Long pacienteId,
        String pacienteNombre,
        LocalDateTime accedidoEn
) {
    public static AccesoHistorialResponse from(AccesoHistorial acceso) {
        var quien = acceso.getUsuario();
        var paciente = acceso.getPaciente().getUsuario();
        return new AccesoHistorialResponse(
                acceso.getId(),
                quien.getId(),
                quien.getEmail(),
                quien.getNombre() + " " + quien.getApellido(),
                quien.getRol(),
                acceso.getPaciente().getId(),
                paciente.getNombre() + " " + paciente.getApellido(),
                acceso.getAccedidoEn());
    }
}
