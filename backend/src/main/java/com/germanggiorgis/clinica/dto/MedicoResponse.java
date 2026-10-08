package com.germanggiorgis.clinica.dto;

import com.germanggiorgis.clinica.model.Medico;

import java.util.List;

public record MedicoResponse(
        Long id,
        String nombre,
        String apellido,
        String matricula,
        List<EspecialidadResponse> especialidades,
        /** Un médico desactivado deja de verse y de poder reservarse; solo el personal lo ve (con este flag en false). */
        boolean activo
) {
    public static MedicoResponse from(Medico medico) {
        var usuario = medico.getUsuario();
        return new MedicoResponse(
                medico.getId(),
                usuario.getNombre(),
                usuario.getApellido(),
                medico.getMatricula(),
                medico.getEspecialidades().stream().map(EspecialidadResponse::from).toList(),
                usuario.isActivo()
        );
    }
}
