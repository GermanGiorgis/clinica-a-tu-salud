package com.germanggiorgis.clinica.controller;

import com.germanggiorgis.clinica.dto.CancelarTurnoRequest;
import com.germanggiorgis.clinica.dto.CrearTurnoRequest;
import com.germanggiorgis.clinica.dto.TurnoResponse;
import com.germanggiorgis.clinica.service.TurnoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/turnos")
@RequiredArgsConstructor
public class TurnoController {

    private final TurnoService turnoService;

    @GetMapping("/mios")
    public List<TurnoResponse> misTurnos() {
        return turnoService.misTurnos();
    }

    @PostMapping
    public ResponseEntity<TurnoResponse> crear(@Valid @RequestBody CrearTurnoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(turnoService.crear(request));
    }

    @PatchMapping("/{id}/cancelar")
    public TurnoResponse cancelar(@PathVariable Long id, @Valid @RequestBody CancelarTurnoRequest request) {
        return turnoService.cancelar(id, request);
    }

    @PatchMapping("/{id}/confirmar")
    public TurnoResponse confirmar(@PathVariable Long id) {
        return turnoService.confirmar(id);
    }
}
