package com.germanggiorgis.clinica.controller;

import com.germanggiorgis.clinica.dto.CrearEncuestaRequest;
import com.germanggiorgis.clinica.dto.EncuestaResponse;
import com.germanggiorgis.clinica.service.EncuestaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/turnos/{turnoId}/encuesta")
@RequiredArgsConstructor
public class EncuestaController {

    private final EncuestaService encuestaService;

    @PostMapping
    public ResponseEntity<EncuestaResponse> crear(@PathVariable Long turnoId, @Valid @RequestBody CrearEncuestaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(encuestaService.crear(turnoId, request));
    }

    @GetMapping
    public EncuestaResponse obtener(@PathVariable Long turnoId) {
        return encuestaService.obtenerPorTurno(turnoId);
    }
}
