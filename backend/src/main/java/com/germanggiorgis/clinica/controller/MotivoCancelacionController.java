package com.germanggiorgis.clinica.controller;

import com.germanggiorgis.clinica.dto.MotivoCancelacionRequest;
import com.germanggiorgis.clinica.dto.MotivoCancelacionResponse;
import com.germanggiorgis.clinica.service.MotivoCancelacionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/motivos-cancelacion")
@RequiredArgsConstructor
public class MotivoCancelacionController {

    private final MotivoCancelacionService motivoCancelacionService;

    @GetMapping
    public List<MotivoCancelacionResponse> listar() {
        return motivoCancelacionService.listar();
    }

    @PostMapping
    public ResponseEntity<MotivoCancelacionResponse> crear(@Valid @RequestBody MotivoCancelacionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(motivoCancelacionService.crear(request));
    }

    @PutMapping("/{id}")
    public MotivoCancelacionResponse actualizar(@PathVariable Long id, @Valid @RequestBody MotivoCancelacionRequest request) {
        return motivoCancelacionService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        motivoCancelacionService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
