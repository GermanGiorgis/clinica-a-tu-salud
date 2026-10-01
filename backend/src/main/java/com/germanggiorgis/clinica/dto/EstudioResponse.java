package com.germanggiorgis.clinica.dto;

import com.germanggiorgis.clinica.model.Estudio;

public record EstudioResponse(
        Long id,
        String nombre,
        String tipo
) {
    public static EstudioResponse from(Estudio estudio) {
        return new EstudioResponse(estudio.getId(), estudio.getNombre(), estudio.getTipo());
    }
}
