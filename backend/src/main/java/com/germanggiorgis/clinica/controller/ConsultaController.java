package com.germanggiorgis.clinica.controller;

import com.germanggiorgis.clinica.dto.ConsultaResponse;
import com.germanggiorgis.clinica.dto.CrearConsultaRequest;
import com.germanggiorgis.clinica.service.ConsultaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/turnos/{turnoId}/consulta")
@RequiredArgsConstructor
public class ConsultaController {

    private final ConsultaService consultaService;

    @PostMapping
    public ResponseEntity<ConsultaResponse> crear(@PathVariable Long turnoId, @Valid @RequestBody CrearConsultaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(consultaService.crear(turnoId, request));
    }

    @GetMapping
    public ConsultaResponse obtener(@PathVariable Long turnoId) {
        return consultaService.obtenerPorTurno(turnoId);
    }
}
