package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.HistorialEntryResponse;
import com.germanggiorgis.clinica.model.Consulta;
import com.germanggiorgis.clinica.model.EstadoTurno;
import com.germanggiorgis.clinica.model.Medico;
import com.germanggiorgis.clinica.model.Paciente;
import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Turno;
import com.germanggiorgis.clinica.model.Usuario;
import com.germanggiorgis.clinica.repository.ConsultaRepository;
import com.germanggiorgis.clinica.repository.MedicoRepository;
import com.germanggiorgis.clinica.repository.PacienteRepository;
import com.germanggiorgis.clinica.repository.TurnoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Regla 8: el historial no es una tabla, se calcula a partir de los turnos
 * ATENDIDO del paciente y sus consultas. Regla 9/10: el paciente solo ve el
 * propio; el médico, el de los pacientes con los que tuvo turnos.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HistorialService {

    private final TurnoRepository turnoRepository;
    private final ConsultaRepository consultaRepository;
    private final PacienteRepository pacienteRepository;
    private final MedicoRepository medicoRepository;
    private final UsuarioContextService usuarioContextService;
    private final ConsultaService consultaService;

    public List<HistorialEntryResponse> mio() {
        Usuario actual = usuarioContextService.getUsuarioActual();
        Paciente paciente = pacienteRepository.findByUsuarioId(actual.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Perfil de paciente no encontrado"));
        return historialDe(paciente.getId());
    }

    public List<HistorialEntryResponse> dePaciente(Long pacienteId) {
        Usuario actual = usuarioContextService.getUsuarioActual();
        if (actual.getRol() == Rol.MEDICO) {
            Medico medico = medicoRepository.findByUsuarioId(actual.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Perfil de médico no encontrado"));
            if (!turnoRepository.existsByMedicoIdAndPacienteId(medico.getId(), pacienteId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nunca tuviste un turno con ese paciente");
            }
        } else if (actual.getRol() != Rol.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No podés ver el historial de otro paciente");
        }
        return historialDe(pacienteId);
    }

    private List<HistorialEntryResponse> historialDe(Long pacienteId) {
        return turnoRepository.findByPacienteIdAndEstadoOrderByInicioDesc(pacienteId, EstadoTurno.ATENDIDO).stream()
                .map(this::armarEntrada)
                .toList();
    }

    private HistorialEntryResponse armarEntrada(Turno turno) {
        Consulta consulta = consultaRepository.findByTurnoId(turno.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Turno atendido sin consulta (dato inconsistente)"));
        var medicoUsuario = turno.getMedico().getUsuario();
        return new HistorialEntryResponse(
                turno.getId(),
                turno.getInicio(),
                medicoUsuario.getNombre() + " " + medicoUsuario.getApellido(),
                turno.getConsultorio().getNombre(),
                consultaService.armarCompleta(consulta)
        );
    }
}
