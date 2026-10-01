package com.germanggiorgis.clinica.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CrearRecetaRequest(
        @Size(max = 500) String observaciones,
        @NotEmpty @Valid List<ItemRecetaRequest> items
) {
}
