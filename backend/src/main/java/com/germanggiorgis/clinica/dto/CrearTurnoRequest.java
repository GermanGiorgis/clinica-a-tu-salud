package com.germanggiorgis.clinica.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CrearTurnoRequest(
        @NotNull Long medicoId,
        @NotNull LocalDateTime inicio,
        @Size(max = 500) String motivoConsulta,
        /** Solo la usa un médico o admin reservando a nombre de otra persona. */
        String pacienteDni
) {
}
