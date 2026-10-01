package com.germanggiorgis.clinica.controller;

import com.germanggiorgis.clinica.dto.CrearFacturaRequest;
import com.germanggiorgis.clinica.dto.FacturaResponse;
import com.germanggiorgis.clinica.service.FacturaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/turnos/{turnoId}/factura")
@RequiredArgsConstructor
public class FacturaController {

    private final FacturaService facturaService;

    @PostMapping
    public ResponseEntity<FacturaResponse> crear(@PathVariable Long turnoId, @Valid @RequestBody CrearFacturaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(facturaService.crear(turnoId, request));
    }

    @GetMapping
    public FacturaResponse obtener(@PathVariable Long turnoId) {
        return facturaService.obtenerPorTurno(turnoId);
    }
}
