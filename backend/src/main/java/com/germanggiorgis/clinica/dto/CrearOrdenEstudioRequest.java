package com.germanggiorgis.clinica.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CrearOrdenEstudioRequest(
        @NotNull Long estudioId,
        @Size(max = 500) String indicaciones
) {
}
