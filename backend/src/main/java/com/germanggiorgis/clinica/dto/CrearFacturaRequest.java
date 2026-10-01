package com.germanggiorgis.clinica.dto;

import com.germanggiorgis.clinica.model.MedioPago;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CrearFacturaRequest(
        @NotNull MedioPago medioPago,
        @NotEmpty @Valid List<ItemFacturaRequest> items
) {
}
