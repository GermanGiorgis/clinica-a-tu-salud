package com.germanggiorgis.clinica.controller;

import com.germanggiorgis.clinica.dto.AccesoHistorialResponse;
import com.germanggiorgis.clinica.service.AuditoriaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Todo acá requiere rol ADMIN (ver SecurityConfig). */
@RestController
@RequestMapping("/api/auditoria")
@RequiredArgsConstructor
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    @GetMapping("/accesos-historial")
    public List<AccesoHistorialResponse> accesosHistorial(@RequestParam(required = false) Long pacienteId) {
        return auditoriaService.accesosHistorial(pacienteId);
    }
}
