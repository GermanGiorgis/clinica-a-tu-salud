package com.germanggiorgis.clinica.controller;

import com.germanggiorgis.clinica.dto.PacienteResponse;
import com.germanggiorgis.clinica.service.PacienteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Todo acá requiere rol ADMIN (ver SecurityConfig). */
@RestController
@RequestMapping("/api/pacientes")
@RequiredArgsConstructor
public class PacienteController {

    private final PacienteService pacienteService;

    @GetMapping("/pendientes")
    public List<PacienteResponse> pendientes() {
        return pacienteService.pendientes();
    }

    @PatchMapping("/{id}/verificar")
    public PacienteResponse verificar(@PathVariable Long id) {
        return pacienteService.verificar(id);
    }
}
