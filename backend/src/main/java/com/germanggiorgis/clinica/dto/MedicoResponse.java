package com.germanggiorgis.clinica.dto;

import com.germanggiorgis.clinica.model.Medico;

import java.util.List;

public record MedicoResponse(
        Long id,
        String nombre,
        String apellido,
        String matricula,
        List<EspecialidadResponse> especialidades
) {
    public static MedicoResponse from(Medico medico) {
        var usuario = medico.getUsuario();
        return new MedicoResponse(
                medico.getId(),
                usuario.getNombre(),
                usuario.getApellido(),
                medico.getMatricula(),
                medico.getEspecialidades().stream().map(EspecialidadResponse::from).toList()
        );
    }
}
