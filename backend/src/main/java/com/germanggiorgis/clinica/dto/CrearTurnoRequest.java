package com.germanggiorgis.clinica.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CrearTurnoRequest(
        @NotNull Long medicoId,
        @NotNull LocalDateTime inicio,
        @Size(max = 500) String motivoConsulta,
        /** Solo la usa un médico o admin reservando a nombre de otra persona. */
        @Size(max = 20) String pacienteDni,
        /** Opcional: con qué afiliación propia del paciente se atiende este turno. */
        Long afiliacionId
) {
}
