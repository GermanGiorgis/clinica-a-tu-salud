package com.germanggiorgis.clinica.dto;

import com.germanggiorgis.clinica.model.ItemReceta;

public record ItemRecetaResponse(
        Long id,
        Long medicamentoId,
        String medicamentoNombre,
        String dosis,
        String frecuencia,
        Integer duracionDias
) {
    public static ItemRecetaResponse from(ItemReceta item) {
        return new ItemRecetaResponse(
                item.getId(),
                item.getMedicamento().getId(),
                item.getMedicamento().getNombre(),
                item.getDosis(),
                item.getFrecuencia(),
                item.getDuracionDias()
        );
    }
}
