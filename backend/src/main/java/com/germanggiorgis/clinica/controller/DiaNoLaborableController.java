package com.germanggiorgis.clinica.controller;

import com.germanggiorgis.clinica.dto.CancelarTurnoRequest;
import com.germanggiorgis.clinica.dto.CrearDiaNoLaborableRequest;
import com.germanggiorgis.clinica.dto.DiaNoLaborableResponse;
import com.germanggiorgis.clinica.dto.TurnoAfectadoResponse;
import com.germanggiorgis.clinica.service.DiaNoLaborableService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Todo acá requiere rol ADMIN o RECEPCIONISTA (ver SecurityConfig). */
@RestController
@RequestMapping("/api/dias-no-laborables")
@RequiredArgsConstructor
public class DiaNoLaborableController {

    private final DiaNoLaborableService diaNoLaborableService;

    @GetMapping
    public List<DiaNoLaborableResponse> listar() {
        return diaNoLaborableService.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DiaNoLaborableResponse crear(@Valid @RequestBody CrearDiaNoLaborableRequest request) {
        return diaNoLaborableService.crear(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        diaNoLaborableService.eliminar(id);
    }

    @GetMapping("/{id}/turnos-afectados")
    public List<TurnoAfectadoResponse> turnosAfectados(@PathVariable Long id) {
        return diaNoLaborableService.turnosAfectados(id);
    }

    @PostMapping("/{id}/cancelar-turnos")
    public Map<String, Integer> cancelarTurnos(@PathVariable Long id, @Valid @RequestBody CancelarTurnoRequest request) {
        return Map.of("cancelados", diaNoLaborableService.cancelarTurnosAfectados(id, request));
    }
}
