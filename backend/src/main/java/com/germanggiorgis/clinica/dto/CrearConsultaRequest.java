package com.germanggiorgis.clinica.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CrearConsultaRequest(
        @Size(max = 4000) String notas,
        @NotEmpty @Valid List<DiagnosticoRequest> diagnosticos
) {
}
