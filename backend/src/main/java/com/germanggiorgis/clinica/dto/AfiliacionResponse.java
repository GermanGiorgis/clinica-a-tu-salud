package com.germanggiorgis.clinica.dto;

import com.germanggiorgis.clinica.model.Afiliacion;

import java.time.LocalDate;

public record AfiliacionResponse(
        Long id,
        Long obraSocialId,
        String obraSocialNombre,
        String numeroAfiliado,
        String plan,
        LocalDate vigenteDesde,
        LocalDate vigenteHasta
) {
    public static AfiliacionResponse from(Afiliacion afiliacion) {
        return new AfiliacionResponse(
                afiliacion.getId(),
                afiliacion.getObraSocial().getId(),
                afiliacion.getObraSocial().getNombre(),
                afiliacion.getNumeroAfiliado(),
                afiliacion.getPlan(),
                afiliacion.getVigenteDesde(),
                afiliacion.getVigenteHasta()
        );
    }
}
