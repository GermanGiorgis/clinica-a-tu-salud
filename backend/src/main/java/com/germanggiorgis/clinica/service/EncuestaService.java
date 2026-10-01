package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.CrearEncuestaRequest;
import com.germanggiorgis.clinica.dto.EncuestaResponse;
import com.germanggiorgis.clinica.model.Encuesta;
import com.germanggiorgis.clinica.model.EstadoTurno;
import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Turno;
import com.germanggiorgis.clinica.model.Usuario;
import com.germanggiorgis.clinica.repository.EncuestaRepository;
import com.germanggiorgis.clinica.repository.TurnoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Regla: una encuesta por turno, solo la carga el paciente dueño, y solo si ya fue atendido. */
@Service
@RequiredArgsConstructor
@Transactional
public class EncuestaService {

    private final EncuestaRepository encuestaRepository;
    private final TurnoRepository turnoRepository;
    private final UsuarioContextService usuarioContextService;

    public EncuestaResponse crear(Long turnoId, CrearEncuestaRequest request) {
        Usuario actual = usuarioContextService.getUsuarioActual();
        Turno turno = obtenerTurno(turnoId);

        if (actual.getRol() != Rol.PACIENTE || !turno.getPaciente().getUsuario().getId().equals(actual.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el paciente del turno puede calificarlo");
        }
        if (turno.getEstado() != EstadoTurno.ATENDIDO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se puede calificar un turno ya atendido");
        }
        if (encuestaRepository.findByTurnoId(turnoId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese turno ya tiene una encuesta cargada");
        }

        Encuesta encuesta = encuestaRepository.save(Encuesta.builder()
                .turno(turno)
                .puntaje(request.puntaje())
                .comentario(request.comentario())
                .build());
        return EncuestaResponse.from(encuesta);
    }

    @Transactional(readOnly = true)
    public EncuestaResponse obtenerPorTurno(Long turnoId) {
        Usuario actual = usuarioContextService.getUsuarioActual();
        Turno turno = obtenerTurno(turnoId);
        boolean puedeVer = actual.getRol() == Rol.ADMIN
                || (actual.getRol() == Rol.MEDICO && turno.getMedico().getUsuario().getId().equals(actual.getId()))
                || (actual.getRol() == Rol.PACIENTE && turno.getPaciente().getUsuario().getId().equals(actual.getId()));
        if (!puedeVer) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No podés ver esta encuesta");
        }
        return encuestaRepository.findByTurnoId(turnoId)
                .map(EncuestaResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ese turno todavía no tiene encuesta"));
    }

    private Turno obtenerTurno(Long id) {
        return turnoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Turno no encontrado"));
    }
}
