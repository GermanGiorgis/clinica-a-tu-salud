package com.germanggiorgis.clinica.dto;

import jakarta.validation.constraints.NotBlank;

public record CargarResultadoRequest(
        @NotBlank String resultado
) {
}
