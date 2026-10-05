package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.HistorialEntryResponse;
import com.germanggiorgis.clinica.model.AccesoHistorial;
import com.germanggiorgis.clinica.model.Consulta;
import com.germanggiorgis.clinica.model.EstadoTurno;
import com.germanggiorgis.clinica.model.Medico;
import com.germanggiorgis.clinica.model.Paciente;
import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Turno;
import com.germanggiorgis.clinica.model.Usuario;
import com.germanggiorgis.clinica.repository.AccesoHistorialRepository;
import com.germanggiorgis.clinica.repository.ConsultaRepository;
import com.germanggiorgis.clinica.repository.MedicoRepository;
import com.germanggiorgis.clinica.repository.PacienteRepository;
import com.germanggiorgis.clinica.repository.TurnoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Regla 8: el historial no es una tabla, se calcula a partir de los turnos ATENDIDO del paciente y
 * sus consultas. Regla 9/10: el paciente solo ve el propio; un médico ve el de un paciente solo si
 * tiene con él un turno que habilite el acceso (ver {@link #dePaciente}); un admin ve cualquiera.
 * Todo acceso de un médico o un admin al historial de otra persona queda registrado.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HistorialService {

    /** Turnos que habilitan a un médico a ver el historial: el que está por atender o ya atendió. */
    private static final List<EstadoTurno> ESTADOS_QUE_HABILITAN =
            List.of(EstadoTurno.RESERVADO, EstadoTurno.CONFIRMADO, EstadoTurno.ATENDIDO);

    private final TurnoRepository turnoRepository;
    private final ConsultaRepository consultaRepository;
    private final PacienteRepository pacienteRepository;
    private final MedicoRepository medicoRepository;
    private final AccesoHistorialRepository accesoHistorialRepository;
    private final UsuarioContextService usuarioContextService;
    private final ConsultaService consultaService;
    private final Clock clock;

    public List<HistorialEntryResponse> mio() {
        Usuario actual = usuarioContextService.getUsuarioActual();
        Paciente paciente = pacienteRepository.findByUsuarioId(actual.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Perfil de paciente no encontrado"));
        return historialDe(paciente.getId());
    }

    /**
     * Un médico puede ver el historial completo de un paciente si tiene con él un turno vigente o
     * atendido QUE NO CREÓ ÉL MISMO, y la clínica ya verificó la identidad de ese paciente. Lo del
     * creador importa porque un médico puede reservar a nombre de cualquier paciente por DNI: si
     * eso alcanzara para habilitar el acceso, cualquier médico podría leer cualquier historial
     * reservando (y cancelando) un turno. Un turno cancelado o ausente tampoco habilita nada.
     *
     * <p>Se anota el acceso aunque no devuelva nada; el registro lo consulta el admin.
     */
    @Transactional
    public List<HistorialEntryResponse> dePaciente(Long pacienteId) {
        Usuario actual = usuarioContextService.getUsuarioActual();
        Paciente paciente;
        if (actual.getRol() == Rol.MEDICO) {
            Medico medico = medicoRepository.findByUsuarioId(actual.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Perfil de médico no encontrado"));
            if (!turnoRepository.existsByMedicoIdAndPacienteIdAndEstadoInAndCreadoPorIdNot(
                    medico.getId(), pacienteId, ESTADOS_QUE_HABILITAN, actual.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "No tenés un turno con ese paciente que te habilite a ver su historial");
            }
            paciente = obtenerPaciente(pacienteId);
            if (!paciente.isIdentidadVerificada()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "La clínica todavía no verificó la identidad de ese paciente");
            }
        } else if (actual.getRol() == Rol.ADMIN) {
            paciente = obtenerPaciente(pacienteId);
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No podés ver el historial de otro paciente");
        }

        accesoHistorialRepository.save(AccesoHistorial.builder()
                .usuario(actual)
                .paciente(paciente)
                .accedidoEn(LocalDateTime.now(clock))
                .build());
        return historialDe(pacienteId);
    }

    private Paciente obtenerPaciente(Long pacienteId) {
        return pacienteRepository.findById(pacienteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente no encontrado"));
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
