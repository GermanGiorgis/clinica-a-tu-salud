package com.germanggiorgis.clinica.controller;

import com.germanggiorgis.clinica.dto.DisponibilidadResponse;
import com.germanggiorgis.clinica.dto.ProximoDiaResponse;
import com.germanggiorgis.clinica.dto.MedicoResponse;
import com.germanggiorgis.clinica.service.MedicoService;
import com.germanggiorgis.clinica.service.TurnoService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/medicos")
@RequiredArgsConstructor
public class MedicoController {

    private final MedicoService medicoService;
    private final TurnoService turnoService;

    @GetMapping
    public List<MedicoResponse> listar(
            @RequestParam(required = false) Long especialidadId,
            @RequestParam(defaultValue = "false") boolean incluirInactivos
    ) {
        return medicoService.listar(especialidadId, incluirInactivos);
    }

    @GetMapping("/{id}")
    public MedicoResponse obtener(@PathVariable Long id) {
        return medicoService.obtener(id);
    }

    @GetMapping("/{id}/disponibilidad")
    public DisponibilidadResponse disponibilidad(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha
    ) {
        return turnoService.disponibilidad(id, fecha);
    }

    /** El primer día con horarios libres desde {@code desde} (hoy si no se indica); {@code fecha} null si no hay ninguno. */
    @GetMapping("/{id}/proximo-dia-disponible")
    public ProximoDiaResponse proximoDiaDisponible(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde
    ) {
        return new ProximoDiaResponse(turnoService.proximoDiaConHorarios(id, desde).orElse(null));
    }
}
