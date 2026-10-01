package com.germanggiorgis.clinica.dto;

import com.germanggiorgis.clinica.model.EstadoOrdenEstudio;
import com.germanggiorgis.clinica.model.OrdenEstudio;

import java.time.LocalDateTime;

public record OrdenEstudioResponse(
        Long id,
        Long estudioId,
        String estudioNombre,
        EstadoOrdenEstudio estado,
        String indicaciones,
        LocalDateTime emitidaEn,
        ResultadoEstudioResponse resultado
) {
    public static OrdenEstudioResponse from(OrdenEstudio orden, ResultadoEstudioResponse resultado) {
        return new OrdenEstudioResponse(
                orden.getId(),
                orden.getEstudio().getId(),
                orden.getEstudio().getNombre(),
                orden.getEstado(),
                orden.getIndicaciones(),
                orden.getEmitidaEn(),
                resultado
        );
    }
}
