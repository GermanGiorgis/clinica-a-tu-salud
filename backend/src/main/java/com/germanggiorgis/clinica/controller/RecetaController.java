package com.germanggiorgis.clinica.controller;

import com.germanggiorgis.clinica.dto.CrearRecetaRequest;
import com.germanggiorgis.clinica.dto.RecetaResponse;
import com.germanggiorgis.clinica.service.RecetaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/consultas/{consultaId}/recetas")
@RequiredArgsConstructor
public class RecetaController {

    private final RecetaService recetaService;

    @PostMapping
    public ResponseEntity<RecetaResponse> crear(@PathVariable Long consultaId, @Valid @RequestBody CrearRecetaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(recetaService.crear(consultaId, request));
    }
}
