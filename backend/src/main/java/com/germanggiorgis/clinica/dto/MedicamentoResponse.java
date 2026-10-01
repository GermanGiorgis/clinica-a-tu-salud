package com.germanggiorgis.clinica.dto;

import com.germanggiorgis.clinica.model.Medicamento;

public record MedicamentoResponse(
        Long id,
        String nombre,
        String presentacion
) {
    public static MedicamentoResponse from(Medicamento medicamento) {
        return new MedicamentoResponse(medicamento.getId(), medicamento.getNombre(), medicamento.getPresentacion());
    }
}
