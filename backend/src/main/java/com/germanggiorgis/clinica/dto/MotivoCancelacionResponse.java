package com.germanggiorgis.clinica.dto;

import com.germanggiorgis.clinica.model.MotivoCancelacion;

public record MotivoCancelacionResponse(
        Long id,
        String nombre,
        String descripcion
) {
    public static MotivoCancelacionResponse from(MotivoCancelacion motivo) {
        return new MotivoCancelacionResponse(motivo.getId(), motivo.getNombre(), motivo.getDescripcion());
    }
}
