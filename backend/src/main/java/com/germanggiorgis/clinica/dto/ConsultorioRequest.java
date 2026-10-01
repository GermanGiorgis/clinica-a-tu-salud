package com.germanggiorgis.clinica.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConsultorioRequest(
        @NotBlank @Size(max = 100) String nombre,
        @Size(max = 150) String ubicacion,
        Integer piso
) {
}
