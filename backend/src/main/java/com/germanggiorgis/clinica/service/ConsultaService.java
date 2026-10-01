package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.ConsultaResponse;
import com.germanggiorgis.clinica.dto.CrearConsultaRequest;
import com.germanggiorgis.clinica.dto.DiagnosticoResponse;
import com.germanggiorgis.clinica.dto.ItemRecetaResponse;
import com.germanggiorgis.clinica.dto.OrdenEstudioResponse;
import com.germanggiorgis.clinica.dto.RecetaResponse;
import com.germanggiorgis.clinica.dto.ResultadoEstudioResponse;
import com.germanggiorgis.clinica.model.Consulta;
import com.germanggiorgis.clinica.model.Diagnostico;
import com.germanggiorgis.clinica.model.EstadoTurno;
import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Turno;
import com.germanggiorgis.clinica.model.Usuario;
import com.germanggiorgis.clinica.repository.ConsultaRepository;
import com.germanggiorgis.clinica.repository.DiagnosticoRepository;
import com.germanggiorgis.clinica.repository.ItemRecetaRepository;
import com.germanggiorgis.clinica.repository.OrdenEstudioRepository;
import com.germanggiorgis.clinica.repository.RecetaRepository;
import com.germanggiorgis.clinica.repository.ResultadoEstudioRepository;
import com.germanggiorgis.clinica.repository.TurnoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Regla 7 de docs/modelo-datos.md: la Consulta solo existe para turnos
 * CONFIRMADO y es 1:1 con el turno; crearla es lo que pasa el turno a
 * ATENDIDO. Solo el médico del turno (o un admin) la carga.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ConsultaService {

    private final ConsultaRepository consultaRepository;
    private final DiagnosticoRepository diagnosticoRepository;
    private final RecetaRepository recetaRepository;
    private final ItemRecetaRepository itemRecetaRepository;
    private final OrdenEstudioRepository ordenEstudioRepository;
    private final ResultadoEstudioRepository resultadoEstudioRepository;
    private final TurnoRepository turnoRepository;
    private final UsuarioContextService usuarioContextService;

    public ConsultaResponse crear(Long turnoId, CrearConsultaRequest request) {
        Usuario actual = usuarioContextService.getUsuarioActual();
        Turno turno = obtenerTurno(turnoId);
        verificarEsMedicoDelTurnoOAdmin(actual, turno);

        if (turno.getEstado() != EstadoTurno.CONFIRMADO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se puede cargar una consulta para un turno confirmado");
        }
        if (consultaRepository.findByTurnoId(turnoId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese turno ya tiene una consulta cargada");
        }

        Consulta consulta = consultaRepository.save(Consulta.builder().turno(turno).notas(request.notas()).build());
        for (var d : request.diagnosticos()) {
            diagnosticoRepository.save(Diagnostico.builder()
                    .consulta(consulta)
                    .descripcion(d.descripcion())
                    .codigoCie10(d.codigoCie10())
                    .build());
        }

        turno.setEstado(EstadoTurno.ATENDIDO);
        turnoRepository.save(turno);

        return armarCompleta(consulta);
    }

    @Transactional(readOnly = true)
    public ConsultaResponse obtenerPorTurno(Long turnoId) {
        Usuario actual = usuarioContextService.getUsuarioActual();
        Turno turno = obtenerTurno(turnoId);
        verificarPuedeVer(actual, turno);
        Consulta consulta = consultaRepository.findByTurnoId(turnoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ese turno todavía no tiene consulta"));
        return armarCompleta(consulta);
    }

    /** Arma la respuesta completa (diagnósticos, recetas con ítems, órdenes con resultado); la usa también HistorialService. */
    public ConsultaResponse armarCompleta(Consulta consulta) {
        List<DiagnosticoResponse> diagnosticos = diagnosticoRepository.findByConsultaId(consulta.getId()).stream()
                .map(DiagnosticoResponse::from)
                .toList();
        List<RecetaResponse> recetas = recetaRepository.findByConsultaId(consulta.getId()).stream()
                .map(receta -> RecetaResponse.from(receta, itemRecetaRepository.findByRecetaId(receta.getId()).stream()
                        .map(ItemRecetaResponse::from)
                        .toList()))
                .toList();
        List<OrdenEstudioResponse> ordenesEstudio = ordenEstudioRepository.findByConsultaId(consulta.getId()).stream()
                .map(orden -> OrdenEstudioResponse.from(orden, resultadoEstudioRepository.findByOrdenEstudioId(orden.getId())
                        .map(ResultadoEstudioResponse::from)
                        .orElse(null)))
                .toList();
        return ConsultaResponse.from(consulta, diagnosticos, recetas, ordenesEstudio);
    }

    private void verificarEsMedicoDelTurnoOAdmin(Usuario actual, Turno turno) {
        boolean ok = actual.getRol() == Rol.ADMIN
                || (actual.getRol() == Rol.MEDICO && turno.getMedico().getUsuario().getId().equals(actual.getId()));
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el médico del turno o un admin puede cargar la consulta");
        }
    }

    private void verificarPuedeVer(Usuario actual, Turno turno) {
        boolean ok = actual.getRol() == Rol.ADMIN
                || (actual.getRol() == Rol.MEDICO && turno.getMedico().getUsuario().getId().equals(actual.getId()))
                || (actual.getRol() == Rol.PACIENTE && turno.getPaciente().getUsuario().getId().equals(actual.getId()));
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No podés ver esta consulta");
        }
    }

    private Turno obtenerTurno(Long id) {
        return turnoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Turno no encontrado"));
    }
}
