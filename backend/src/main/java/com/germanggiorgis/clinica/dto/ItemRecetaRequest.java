package com.germanggiorgis.clinica.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ItemRecetaRequest(
        @NotNull Long medicamentoId,
        @NotBlank @Size(max = 50) String dosis,
        @NotBlank @Size(max = 50) String frecuencia,
        @NotNull @Positive Integer duracionDias
) {
}
