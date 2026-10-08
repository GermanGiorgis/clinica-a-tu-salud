package com.germanggiorgis.clinica.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** El mismo turno, con el mismo médico, en otro horario. Para cambiar de médico hay que cancelar y reservar de nuevo. */
public record ReprogramarTurnoRequest(
        @NotNull LocalDateTime inicio
) {
}
