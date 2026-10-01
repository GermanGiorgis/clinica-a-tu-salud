package com.germanggiorgis.clinica.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MotivoCancelacionRequest(
        @NotBlank @Size(max = 100) String nombre,
        @Size(max = 255) String descripcion
) {
}
