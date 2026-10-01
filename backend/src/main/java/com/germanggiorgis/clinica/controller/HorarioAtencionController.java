package com.germanggiorgis.clinica.controller;

import com.germanggiorgis.clinica.dto.HorarioAtencionRequest;
import com.germanggiorgis.clinica.dto.HorarioAtencionResponse;
import com.germanggiorgis.clinica.service.HorarioAtencionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/horarios-atencion")
@RequiredArgsConstructor
public class HorarioAtencionController {

    private final HorarioAtencionService horarioAtencionService;

    @GetMapping
    public List<HorarioAtencionResponse> listar(@RequestParam(required = false) Long medicoId) {
        return horarioAtencionService.listar(medicoId);
    }

    @PostMapping
    public ResponseEntity<HorarioAtencionResponse> crear(@Valid @RequestBody HorarioAtencionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(horarioAtencionService.crear(request));
    }

    @PutMapping("/{id}")
    public HorarioAtencionResponse actualizar(@PathVariable Long id, @Valid @RequestBody HorarioAtencionRequest request) {
        return horarioAtencionService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        horarioAtencionService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
