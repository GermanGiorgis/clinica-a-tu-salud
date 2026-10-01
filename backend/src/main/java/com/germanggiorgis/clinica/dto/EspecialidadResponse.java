package com.germanggiorgis.clinica.dto;

import com.germanggiorgis.clinica.model.Especialidad;

public record EspecialidadResponse(
        Long id,
        String nombre,
        String descripcion
) {
    public static EspecialidadResponse from(Especialidad especialidad) {
        return new EspecialidadResponse(especialidad.getId(), especialidad.getNombre(), especialidad.getDescripcion());
    }
}
