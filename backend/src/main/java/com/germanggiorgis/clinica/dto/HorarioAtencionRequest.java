package com.germanggiorgis.clinica.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

public record HorarioAtencionRequest(
        @NotNull Long medicoId,
        @NotNull Long consultorioId,
        @NotNull DayOfWeek diaSemana,
        @NotNull LocalTime horaInicio,
        @NotNull LocalTime horaFin,
        @NotNull @Min(5) @Max(240) Integer duracionTurnoMin,
        @NotNull LocalDate vigenteDesde,
        LocalDate vigenteHasta
) {
}
