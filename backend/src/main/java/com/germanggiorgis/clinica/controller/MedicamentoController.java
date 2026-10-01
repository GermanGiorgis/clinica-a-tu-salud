package com.germanggiorgis.clinica.controller;

import com.germanggiorgis.clinica.dto.MedicamentoRequest;
import com.germanggiorgis.clinica.dto.MedicamentoResponse;
import com.germanggiorgis.clinica.service.MedicamentoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/medicamentos")
@RequiredArgsConstructor
public class MedicamentoController {

    private final MedicamentoService medicamentoService;

    @GetMapping
    public List<MedicamentoResponse> listar() {
        return medicamentoService.listar();
    }

    @PostMapping
    public ResponseEntity<MedicamentoResponse> crear(@Valid @RequestBody MedicamentoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(medicamentoService.crear(request));
    }

    @PutMapping("/{id}")
    public MedicamentoResponse actualizar(@PathVariable Long id, @Valid @RequestBody MedicamentoRequest request) {
        return medicamentoService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        medicamentoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
