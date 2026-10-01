package com.germanggiorgis.clinica.dto;

import com.germanggiorgis.clinica.model.Receta;

import java.time.LocalDateTime;
import java.util.List;

public record RecetaResponse(
        Long id,
        Long consultaId,
        String observaciones,
        LocalDateTime emitidaEn,
        List<ItemRecetaResponse> items
) {
    public static RecetaResponse from(Receta receta, List<ItemRecetaResponse> items) {
        return new RecetaResponse(
                receta.getId(),
                receta.getConsulta().getId(),
                receta.getObservaciones(),
                receta.getEmitidaEn(),
                items
        );
    }
}
