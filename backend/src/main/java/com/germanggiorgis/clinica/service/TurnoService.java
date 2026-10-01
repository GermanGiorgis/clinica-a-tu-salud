package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.CancelarTurnoRequest;
import com.germanggiorgis.clinica.dto.CrearTurnoRequest;
import com.germanggiorgis.clinica.dto.DisponibilidadResponse;
import com.germanggiorgis.clinica.dto.TurnoResponse;
import com.germanggiorgis.clinica.model.EstadoTurno;
import com.germanggiorgis.clinica.model.HorarioAtencion;
import com.germanggiorgis.clinica.model.Medico;
import com.germanggiorgis.clinica.model.MotivoCancelacion;
import com.germanggiorgis.clinica.model.Paciente;
import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Turno;
import com.germanggiorgis.clinica.model.Usuario;
import com.germanggiorgis.clinica.repository.HorarioAtencionRepository;
import com.germanggiorgis.clinica.repository.MedicoRepository;
import com.germanggiorgis.clinica.repository.MotivoCancelacionRepository;
import com.germanggiorgis.clinica.repository.PacienteRepository;
import com.germanggiorgis.clinica.repository.TurnoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * El corazón del proyecto: calcula horarios libres a partir de
 * HorarioAtencion menos los turnos activos (regla 1 de docs/modelo-datos.md)
 * y aplica las reglas de reserva y cancelación (reglas 2 a 5). El índice
 * único parcial de la migración es el respaldo final contra dos reservas
 * simultáneas; acá se valida antes, para dar un mensaje claro en el caso
 * normal, no solo en la carrera rara.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TurnoService {

    private static final int VENTANA_RESERVA_DIAS = 60;
    private static final int ANTICIPACION_MINIMA_CANCELACION_HORAS = 2;

    private final TurnoRepository turnoRepository;
    private final HorarioAtencionRepository horarioAtencionRepository;
    private final MedicoRepository medicoRepository;
    private final PacienteRepository pacienteRepository;
    private final MotivoCancelacionRepository motivoCancelacionRepository;
    private final UsuarioContextService usuarioContextService;

    @Transactional(readOnly = true)
    public DisponibilidadResponse disponibilidad(Long medicoId, LocalDate fecha) {
        if (fecha.isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha no puede ser pasada");
        }
        medicoRepository.findById(medicoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Médico no encontrado"));

        List<HorarioAtencion> horarios = horariosVigentes(medicoId, fecha);
        if (horarios.isEmpty()) {
            return new DisponibilidadResponse(fecha, List.of());
        }

        Set<LocalTime> ocupados = turnoRepository
                .findByMedicoIdAndInicioBetweenAndEstadoNot(
                        medicoId, fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay(), EstadoTurno.CANCELADO)
                .stream()
                .map(t -> t.getInicio().toLocalTime())
                .collect(Collectors.toSet());

        LocalDateTime ahora = LocalDateTime.now();
        List<LocalTime> libres = new ArrayList<>();
        for (HorarioAtencion horario : horarios) {
            // Se itera por cantidad de slots (no sumando minutos hasta "pasarse" de
            // horaFin): con un horario que llega cerca de la medianoche, sumarle la
            // duración a un cursor cercano a las 23:xx puede dar una hora "menor"
            // (p. ej. 23:45 + 30min = 00:15), que LocalTime nunca ve como "after"
            // horaFin, y el for original quedaba en loop infinito.
            long totalMinutos = Duration.between(horario.getHoraInicio(), horario.getHoraFin()).toMinutes();
            int cantidadSlots = (int) (totalMinutos / horario.getDuracionTurnoMin());
            for (int i = 0; i < cantidadSlots; i++) {
                LocalTime cursor = horario.getHoraInicio().plusMinutes((long) i * horario.getDuracionTurnoMin());
                LocalDateTime inicioSlot = LocalDateTime.of(fecha, cursor);
                if (!ocupados.contains(cursor) && inicioSlot.isAfter(ahora)) {
                    libres.add(cursor);
                }
            }
        }
        libres.sort(Comparator.naturalOrder());
        return new DisponibilidadResponse(fecha, libres);
    }

    public TurnoResponse crear(CrearTurnoRequest request) {
        Usuario usuarioActual = usuarioContextService.getUsuarioActual();
        Paciente paciente = resolverPacienteParaReserva(usuarioActual, request.pacienteDni());
        Medico medico = medicoRepository.findById(request.medicoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Médico no encontrado"));

        LocalDateTime inicio = request.inicio();
        LocalDate fecha = inicio.toLocalDate();
        if (inicio.isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se puede reservar en el pasado");
        }
        if (fecha.isAfter(LocalDate.now().plusDays(VENTANA_RESERVA_DIAS))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se puede reservar con tanta anticipación");
        }

        HorarioAtencion horario = horariosVigentes(medico.getId(), fecha).stream()
                .filter(h -> encajaEnHorario(inicio.toLocalTime(), h))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El horario elegido no corresponde a la agenda del médico"));

        LocalDateTime fin = inicio.plusMinutes(horario.getDuracionTurnoMin());

        boolean pacienteOcupado = turnoRepository
                .findByPacienteIdAndEstadoNotAndInicioBetween(
                        paciente.getId(), EstadoTurno.CANCELADO, fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay())
                .stream()
                .anyMatch(t -> inicio.isBefore(t.getFin()) && t.getInicio().isBefore(fin));
        if (pacienteOcupado) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya tenés un turno reservado en ese horario");
        }

        Turno turno = Turno.builder()
                .paciente(paciente)
                .medico(medico)
                .consultorio(horario.getConsultorio())
                .creadoPor(usuarioActual)
                .inicio(inicio)
                .fin(fin)
                .motivoConsulta(request.motivoConsulta())
                .build();

        try {
            return TurnoResponse.from(turnoRepository.saveAndFlush(turno));
        } catch (DataIntegrityViolationException ex) {
            // Perdió la carrera contra el índice único parcial: alguien reservó ese
            // mismo horario una fracción de segundo antes.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese horario ya fue reservado, elegí otro");
        }
    }

    @Transactional(readOnly = true)
    public List<TurnoResponse> misTurnos() {
        Usuario usuarioActual = usuarioContextService.getUsuarioActual();
        Paciente paciente = pacienteRepository.findByUsuarioId(usuarioActual.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Perfil de paciente no encontrado"));
        return turnoRepository.findByPacienteIdOrderByInicioDesc(paciente.getId()).stream()
                .map(TurnoResponse::from)
                .toList();
    }

    /**
     * Agenda de un médico. El propio médico ve la suya (no puede pedir la de
     * otro); un admin puede ver la de cualquiera, pero tiene que indicar cuál.
     */
    @Transactional(readOnly = true)
    public List<TurnoResponse> agenda(Long medicoIdParam, LocalDate fecha) {
        Usuario usuarioActual = usuarioContextService.getUsuarioActual();
        Long medicoId;
        if (usuarioActual.getRol() == Rol.MEDICO) {
            Medico propio = medicoRepository.findByUsuarioId(usuarioActual.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Perfil de médico no encontrado"));
            if (medicoIdParam != null && !medicoIdParam.equals(propio.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No podés ver la agenda de otro médico");
            }
            medicoId = propio.getId();
        } else if (usuarioActual.getRol() == Rol.ADMIN) {
            if (medicoIdParam == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indicá qué médico querés ver");
            }
            medicoId = medicoIdParam;
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un médico o un admin puede ver una agenda");
        }

        List<Turno> turnos = fecha != null
                ? turnoRepository.findByMedicoIdAndInicioBetweenOrderByInicioAsc(
                        medicoId, fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay())
                : turnoRepository.findByMedicoIdAndInicioGreaterThanEqualOrderByInicioAsc(
                        medicoId, LocalDate.now().atStartOfDay());
        return turnos.stream().map(TurnoResponse::from).toList();
    }

    public TurnoResponse cancelar(Long turnoId, CancelarTurnoRequest request) {
        Usuario usuarioActual = usuarioContextService.getUsuarioActual();
        Turno turno = obtener(turnoId);

        if (!puedeGestionar(usuarioActual, turno)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No podés cancelar el turno de otra persona");
        }
        if (turno.getEstado() != EstadoTurno.RESERVADO && turno.getEstado() != EstadoTurno.CONFIRMADO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ese turno ya no se puede cancelar");
        }
        if (usuarioActual.getRol() == Rol.PACIENTE) {
            long horasHastaElTurno = Duration.between(LocalDateTime.now(), turno.getInicio()).toHours();
            if (horasHastaElTurno < ANTICIPACION_MINIMA_CANCELACION_HORAS) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Para cancelar necesitás al menos " + ANTICIPACION_MINIMA_CANCELACION_HORAS + " horas de anticipación");
            }
        }

        MotivoCancelacion motivo = motivoCancelacionRepository.findById(request.motivoCancelacionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Motivo de cancelación no encontrado"));

        turno.setEstado(EstadoTurno.CANCELADO);
        turno.setMotivoCancelacion(motivo);
        return TurnoResponse.from(turnoRepository.save(turno));
    }

    public TurnoResponse confirmar(Long turnoId) {
        Usuario usuarioActual = usuarioContextService.getUsuarioActual();
        Turno turno = obtener(turnoId);

        if (!puedeGestionar(usuarioActual, turno)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No podés confirmar el turno de otra persona");
        }
        if (turno.getEstado() != EstadoTurno.RESERVADO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se puede confirmar un turno reservado");
        }

        turno.setEstado(EstadoTurno.CONFIRMADO);
        return TurnoResponse.from(turnoRepository.save(turno));
    }

    public TurnoResponse marcarAusente(Long turnoId) {
        Usuario usuarioActual = usuarioContextService.getUsuarioActual();
        Turno turno = obtener(turnoId);

        if (usuarioActual.getRol() == Rol.PACIENTE || !puedeGestionar(usuarioActual, turno)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el médico del turno o un admin puede marcar esto");
        }
        if (turno.getEstado() != EstadoTurno.CONFIRMADO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se puede marcar ausente un turno confirmado");
        }

        turno.setEstado(EstadoTurno.AUSENTE);
        return TurnoResponse.from(turnoRepository.save(turno));
    }

    /** Dueño del turno (paciente), médico asignado a él, o cualquier admin. */
    private boolean puedeGestionar(Usuario usuarioActual, Turno turno) {
        return switch (usuarioActual.getRol()) {
            case ADMIN -> true;
            case MEDICO -> turno.getMedico().getUsuario().getId().equals(usuarioActual.getId());
            case PACIENTE -> turno.getPaciente().getUsuario().getId().equals(usuarioActual.getId());
        };
    }

    /**
     * El paciente reserva para sí mismo; un médico o admin reserva a nombre
     * de otra persona indicando su DNI (como pedirlo en mostrador).
     */
    private Paciente resolverPacienteParaReserva(Usuario usuarioActual, String pacienteDni) {
        if (usuarioActual.getRol() == Rol.PACIENTE) {
            return pacienteRepository.findByUsuarioId(usuarioActual.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Perfil de paciente no encontrado"));
        }
        if (pacienteDni == null || pacienteDni.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el DNI del paciente");
        }
        return pacienteRepository.findByDni(pacienteDni)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay ningún paciente con ese DNI"));
    }

    private List<HorarioAtencion> horariosVigentes(Long medicoId, LocalDate fecha) {
        return horarioAtencionRepository.findByMedicoIdAndDiaSemana(medicoId, fecha.getDayOfWeek()).stream()
                .filter(h -> !fecha.isBefore(h.getVigenteDesde()) && (h.getVigenteHasta() == null || !fecha.isAfter(h.getVigenteHasta())))
                .toList();
    }

    /**
     * La hora pedida tiene que caer justo en uno de los slots que genera el
     * horario. Se compara todo con Duration.between en vez de sumarle
     * minutos a `hora` (ver el comentario en disponibilidad()): cerca de la
     * medianoche esa suma puede "envolver" a una hora menor y colar un slot
     * que en realidad termina después de horaFin.
     */
    private boolean encajaEnHorario(LocalTime hora, HorarioAtencion horario) {
        if (hora.isBefore(horario.getHoraInicio()) || !hora.isBefore(horario.getHoraFin())) {
            return false;
        }
        long minutosDesdeInicio = Duration.between(horario.getHoraInicio(), hora).toMinutes();
        if (minutosDesdeInicio % horario.getDuracionTurnoMin() != 0) {
            return false;
        }
        long minutosHastaFin = Duration.between(hora, horario.getHoraFin()).toMinutes();
        return minutosHastaFin >= horario.getDuracionTurnoMin();
    }

    private Turno obtener(Long id) {
        return turnoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Turno no encontrado"));
    }
}
