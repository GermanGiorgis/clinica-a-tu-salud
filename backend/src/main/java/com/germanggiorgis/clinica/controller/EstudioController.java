package com.germanggiorgis.clinica.controller;

import com.germanggiorgis.clinica.dto.EstudioRequest;
import com.germanggiorgis.clinica.dto.EstudioResponse;
import com.germanggiorgis.clinica.service.EstudioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/estudios")
@RequiredArgsConstructor
public class EstudioController {

    private final EstudioService estudioService;

    @GetMapping
    public List<EstudioResponse> listar() {
        return estudioService.listar();
    }

    @PostMapping
    public ResponseEntity<EstudioResponse> crear(@Valid @RequestBody EstudioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(estudioService.crear(request));
    }

    @PutMapping("/{id}")
    public EstudioResponse actualizar(@PathVariable Long id, @Valid @RequestBody EstudioRequest request) {
        return estudioService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        estudioService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
