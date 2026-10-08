package com.germanggiorgis.clinica.controller;

import com.germanggiorgis.clinica.dto.CancelarTurnoRequest;
import com.germanggiorgis.clinica.dto.CrearTurnoRequest;
import com.germanggiorgis.clinica.dto.ReprogramarTurnoRequest;
import com.germanggiorgis.clinica.dto.TurnoResponse;
import com.germanggiorgis.clinica.service.TurnoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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

    /** Agenda de un médico: el propio médico ve la suya, el admin tiene que indicar medicoId. */
    @GetMapping("/agenda")
    public List<TurnoResponse> agenda(
            @RequestParam(required = false) Long medicoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha
    ) {
        return turnoService.agenda(medicoId, fecha);
    }

    @PostMapping
    public ResponseEntity<TurnoResponse> crear(@Valid @RequestBody CrearTurnoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(turnoService.crear(request));
    }

    @PatchMapping("/{id}/cancelar")
    public TurnoResponse cancelar(@PathVariable Long id, @Valid @RequestBody CancelarTurnoRequest request) {
        return turnoService.cancelar(id, request);
    }

    @PatchMapping("/{id}/reprogramar")
    public TurnoResponse reprogramar(@PathVariable Long id, @Valid @RequestBody ReprogramarTurnoRequest request) {
        return turnoService.reprogramar(id, request);
    }

    @PatchMapping("/{id}/confirmar")
    public TurnoResponse confirmar(@PathVariable Long id) {
        return turnoService.confirmar(id);
    }

    @PatchMapping("/{id}/marcar-ausente")
    public TurnoResponse marcarAusente(@PathVariable Long id) {
        return turnoService.marcarAusente(id);
    }
}
