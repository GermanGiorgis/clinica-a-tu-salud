package com.germanggiorgis.clinica.dto;

import com.germanggiorgis.clinica.model.Patologia;

import java.util.List;

public record PatologiaResponse(
        Long id,
        String nombre,
        List<EspecialidadResponse> especialidades
) {
    public static PatologiaResponse from(Patologia patologia) {
        return new PatologiaResponse(
                patologia.getId(),
                patologia.getNombre(),
                patologia.getEspecialidades().stream().map(EspecialidadResponse::from).toList()
        );
    }
}
