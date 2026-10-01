package com.germanggiorgis.clinica.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record DisponibilidadResponse(
        LocalDate fecha,
        List<LocalTime> horariosLibres
) {
}
