package com.germanggiorgis.clinica.controller;

import com.germanggiorgis.clinica.dto.AfiliacionResponse;
import com.germanggiorgis.clinica.dto.CrearAfiliacionRequest;
import com.germanggiorgis.clinica.service.AfiliacionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/afiliaciones")
@RequiredArgsConstructor
public class AfiliacionController {

    private final AfiliacionService afiliacionService;

    @GetMapping("/mias")
    public List<AfiliacionResponse> misAfiliaciones() {
        return afiliacionService.misAfiliaciones();
    }

    @PostMapping
    public ResponseEntity<AfiliacionResponse> crear(@Valid @RequestBody CrearAfiliacionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(afiliacionService.crear(request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        afiliacionService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
