package com.germanggiorgis.clinica.dto;

import com.germanggiorgis.clinica.model.DiaNoLaborable;

import java.time.LocalDate;

public record DiaNoLaborableResponse(
        Long id,
        LocalDate desde,
        LocalDate hasta,
        String descripcion,
        Long medicoId,
        String medicoNombre
) {
    public static DiaNoLaborableResponse from(DiaNoLaborable dia) {
        var medico = dia.getMedico();
        return new DiaNoLaborableResponse(
                dia.getId(),
                dia.getDesde(),
                dia.getHasta(),
                dia.getDescripcion(),
                medico != null ? medico.getId() : null,
                medico != null ? medico.getUsuario().getNombre() + " " + medico.getUsuario().getApellido() : null);
    }
}
