package com.germanggiorgis.clinica.dto;

import com.germanggiorgis.clinica.model.Consulta;

import java.time.LocalDateTime;
import java.util.List;

public record ConsultaResponse(
        Long id,
        Long turnoId,
        String notas,
        LocalDateTime creadaEn,
        List<DiagnosticoResponse> diagnosticos,
        List<RecetaResponse> recetas,
        List<OrdenEstudioResponse> ordenesEstudio
) {
    public static ConsultaResponse from(
            Consulta consulta,
            List<DiagnosticoResponse> diagnosticos,
            List<RecetaResponse> recetas,
            List<OrdenEstudioResponse> ordenesEstudio
    ) {
        return new ConsultaResponse(
                consulta.getId(),
                consulta.getTurno().getId(),
                consulta.getNotas(),
                consulta.getCreadaEn(),
                diagnosticos,
                recetas,
                ordenesEstudio
        );
    }
}
