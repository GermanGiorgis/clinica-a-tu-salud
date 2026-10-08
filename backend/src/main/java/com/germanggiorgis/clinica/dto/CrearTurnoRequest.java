package com.germanggiorgis.clinica.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CrearTurnoRequest(
        @NotNull Long medicoId,
        @NotNull LocalDateTime inicio,
        @Size(max = 500) String motivoConsulta,
        /** Solo la usa un médico o admin reservando a nombre de otra persona. */
        @Pattern(regexp = "\\d{6,9}", message = "El DNI tiene que tener entre 6 y 9 números, sin puntos ni espacios") String pacienteDni,
        /** Opcional: con qué afiliación propia del paciente se atiende este turno. */
        Long afiliacionId
) {
}
