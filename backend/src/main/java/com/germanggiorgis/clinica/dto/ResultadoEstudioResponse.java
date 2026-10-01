package com.germanggiorgis.clinica.dto;

import com.germanggiorgis.clinica.model.ResultadoEstudio;

import java.time.LocalDateTime;

public record ResultadoEstudioResponse(
        Long id,
        String resultado,
        LocalDateTime cargadoEn
) {
    public static ResultadoEstudioResponse from(ResultadoEstudio resultado) {
        return new ResultadoEstudioResponse(resultado.getId(), resultado.getResultado(), resultado.getCargadoEn());
    }
}
