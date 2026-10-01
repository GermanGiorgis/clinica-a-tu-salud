package com.germanggiorgis.clinica.controller;

import com.germanggiorgis.clinica.dto.ObraSocialRequest;
import com.germanggiorgis.clinica.dto.ObraSocialResponse;
import com.germanggiorgis.clinica.service.ObraSocialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/obras-sociales")
@RequiredArgsConstructor
public class ObraSocialController {

    private final ObraSocialService obraSocialService;

    @GetMapping
    public List<ObraSocialResponse> listar() {
        return obraSocialService.listar();
    }

    @PostMapping
    public ResponseEntity<ObraSocialResponse> crear(@Valid @RequestBody ObraSocialRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(obraSocialService.crear(request));
    }

    @PutMapping("/{id}")
    public ObraSocialResponse actualizar(@PathVariable Long id, @Valid @RequestBody ObraSocialRequest request) {
        return obraSocialService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        obraSocialService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
