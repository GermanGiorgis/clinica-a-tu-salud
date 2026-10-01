package com.germanggiorgis.clinica.dto;

import com.germanggiorgis.clinica.model.Patologia;

public record PatologiaResponse(
        Long id,
        String nombre,
        EspecialidadResponse especialidad
) {
    public static PatologiaResponse from(Patologia patologia) {
        return new PatologiaResponse(
                patologia.getId(),
                patologia.getNombre(),
                EspecialidadResponse.from(patologia.getEspecialidad())
        );
    }
}
