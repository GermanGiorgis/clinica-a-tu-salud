package com.germanggiorgis.clinica.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * {@code aviso} explica por qué no hay horarios cuando el día está cerrado (feriado, ausencia del
 * médico); es nulo en un día normal, aunque no queden horarios libres.
 */
public record DisponibilidadResponse(
        LocalDate fecha,
        List<LocalTime> horariosLibres,
        String aviso
) {
    public DisponibilidadResponse(LocalDate fecha, List<LocalTime> horariosLibres) {
        this(fecha, horariosLibres, null);
    }
}
