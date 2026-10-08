package com.germanggiorgis.clinica.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** {@code medicoId} nulo = feriado o cierre de toda la clínica; con valor, ausencia de ese médico. */
public record CrearDiaNoLaborableRequest(
        @NotNull LocalDate desde,
        @NotNull LocalDate hasta,
        @NotBlank @Size(max = 150) String descripcion,
        Long medicoId
) {
}
