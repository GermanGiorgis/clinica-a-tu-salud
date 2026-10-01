package com.germanggiorgis.clinica.controller;

import com.germanggiorgis.clinica.dto.ConsultorioRequest;
import com.germanggiorgis.clinica.dto.ConsultorioResponse;
import com.germanggiorgis.clinica.service.ConsultorioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/consultorios")
@RequiredArgsConstructor
public class ConsultorioController {

    private final ConsultorioService consultorioService;

    @GetMapping
    public List<ConsultorioResponse> listar() {
        return consultorioService.listar();
    }

    @PostMapping
    public ResponseEntity<ConsultorioResponse> crear(@Valid @RequestBody ConsultorioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(consultorioService.crear(request));
    }

    @PutMapping("/{id}")
    public ConsultorioResponse actualizar(@PathVariable Long id, @Valid @RequestBody ConsultorioRequest request) {
        return consultorioService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        consultorioService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
