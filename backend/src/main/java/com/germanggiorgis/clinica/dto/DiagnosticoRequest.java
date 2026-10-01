package com.germanggiorgis.clinica.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DiagnosticoRequest(
        @NotBlank @Size(max = 255) String descripcion,
        @Size(max = 10) String codigoCie10
) {
}
