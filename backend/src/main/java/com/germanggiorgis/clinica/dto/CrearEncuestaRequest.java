package com.germanggiorgis.clinica.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CrearEncuestaRequest(
        @NotNull @Min(1) @Max(5) Integer puntaje,
        @Size(max = 500) String comentario
) {
}
