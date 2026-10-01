package com.germanggiorgis.clinica.controller;

import com.germanggiorgis.clinica.dto.PatologiaRequest;
import com.germanggiorgis.clinica.dto.PatologiaResponse;
import com.germanggiorgis.clinica.service.PatologiaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/patologias")
@RequiredArgsConstructor
public class PatologiaController {

    private final PatologiaService patologiaService;

    @GetMapping
    public List<PatologiaResponse> listar() {
        return patologiaService.listar();
    }

    @PostMapping
    public ResponseEntity<PatologiaResponse> crear(@Valid @RequestBody PatologiaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(patologiaService.crear(request));
    }

    @PutMapping("/{id}")
    public PatologiaResponse actualizar(@PathVariable Long id, @Valid @RequestBody PatologiaRequest request) {
        return patologiaService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        patologiaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
