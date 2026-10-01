package com.germanggiorgis.clinica.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EstudioRequest(
        @NotBlank @Size(max = 150) String nombre,
        @NotBlank @Size(max = 50) String tipo
) {
}
