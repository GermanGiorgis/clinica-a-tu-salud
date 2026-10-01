package com.germanggiorgis.clinica.controller;

import com.germanggiorgis.clinica.dto.EspecialidadRequest;
import com.germanggiorgis.clinica.dto.EspecialidadResponse;
import com.germanggiorgis.clinica.service.EspecialidadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/especialidades")
@RequiredArgsConstructor
public class EspecialidadController {

    private final EspecialidadService especialidadService;

    @GetMapping
    public List<EspecialidadResponse> listar() {
        return especialidadService.listar();
    }

    @PostMapping
    public ResponseEntity<EspecialidadResponse> crear(@Valid @RequestBody EspecialidadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(especialidadService.crear(request));
    }

    @PutMapping("/{id}")
    public EspecialidadResponse actualizar(@PathVariable Long id, @Valid @RequestBody EspecialidadRequest request) {
        return especialidadService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        especialidadService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
