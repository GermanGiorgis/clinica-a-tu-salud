package com.germanggiorgis.clinica.dto;

import com.germanggiorgis.clinica.model.Diagnostico;

public record DiagnosticoResponse(
        Long id,
        String descripcion,
        String codigoCie10
) {
    public static DiagnosticoResponse from(Diagnostico diagnostico) {
        return new DiagnosticoResponse(diagnostico.getId(), diagnostico.getDescripcion(), diagnostico.getCodigoCie10());
    }
}
