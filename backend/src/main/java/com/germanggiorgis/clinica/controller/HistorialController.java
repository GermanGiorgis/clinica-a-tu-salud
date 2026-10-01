package com.germanggiorgis.clinica.controller;

import com.germanggiorgis.clinica.dto.HistorialEntryResponse;
import com.germanggiorgis.clinica.service.HistorialService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/historial")
@RequiredArgsConstructor
public class HistorialController {

    private final HistorialService historialService;

    @GetMapping("/mio")
    public List<HistorialEntryResponse> mio() {
        return historialService.mio();
    }

    @GetMapping("/paciente/{pacienteId}")
    public List<HistorialEntryResponse> dePaciente(@PathVariable Long pacienteId) {
        return historialService.dePaciente(pacienteId);
    }
}
