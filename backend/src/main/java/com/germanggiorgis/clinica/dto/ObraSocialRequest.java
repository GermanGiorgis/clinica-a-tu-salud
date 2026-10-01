package com.germanggiorgis.clinica.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ObraSocialRequest(
        @NotBlank @Size(max = 100) String nombre,
        @Size(max = 30) String telefono
) {
}
