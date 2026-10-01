package com.germanggiorgis.clinica.controller;

import com.germanggiorgis.clinica.dto.CargarResultadoRequest;
import com.germanggiorgis.clinica.dto.CrearOrdenEstudioRequest;
import com.germanggiorgis.clinica.dto.OrdenEstudioResponse;
import com.germanggiorgis.clinica.service.OrdenEstudioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class OrdenEstudioController {

    private final OrdenEstudioService ordenEstudioService;

    @PostMapping("/api/consultas/{consultaId}/ordenes-estudio")
    public ResponseEntity<OrdenEstudioResponse> crear(@PathVariable Long consultaId, @Valid @RequestBody CrearOrdenEstudioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ordenEstudioService.crear(consultaId, request));
    }

    @PatchMapping("/api/ordenes-estudio/{ordenId}/resultado")
    public OrdenEstudioResponse cargarResultado(@PathVariable Long ordenId, @Valid @RequestBody CargarResultadoRequest request) {
        return ordenEstudioService.cargarResultado(ordenId, request);
    }
}
