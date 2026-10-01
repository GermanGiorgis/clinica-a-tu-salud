package com.germanggiorgis.clinica.dto;

import jakarta.validation.constraints.NotNull;

public record CancelarTurnoRequest(
        @NotNull Long motivoCancelacionId
) {
}
