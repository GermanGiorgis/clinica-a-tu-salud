package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.CancelarTurnoRequest;
import com.germanggiorgis.clinica.dto.CrearTurnoRequest;
import com.germanggiorgis.clinica.dto.DisponibilidadResponse;
import com.germanggiorgis.clinica.dto.TurnoResponse;
import com.germanggiorgis.clinica.model.Afiliacion;
import com.germanggiorgis.clinica.model.EstadoTurno;
import com.germanggiorgis.clinica.model.HorarioAtencion;
import com.germanggiorgis.clinica.model.Medico;
import com.germanggiorgis.clinica.model.MotivoCancelacion;
import com.germanggiorgis.clinica.model.Paciente;
import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Turno;
import com.germanggiorgis.clinica.model.Usuario;
import com.germanggiorgis.clinica.model.DiaNoLaborable;
import com.germanggiorgis.clinica.repository.AfiliacionRepository;
import com.germanggiorgis.clinica.repository.DiaNoLaborableRepository;
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

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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
    /** Cuánto se espera a un paciente antes de poder darlo por ausente (regla 19). */
    static final int TOLERANCIA_AUSENCIA_MINUTOS = 20;
    /** Turnos pendientes que un paciente puede tener a la vez (regla 20). */
    static final int MAXIMO_TURNOS_PENDIENTES = 2;
    private static final List<EstadoTurno> PENDIENTES = List.of(EstadoTurno.RESERVADO, EstadoTurno.CONFIRMADO);

    private final TurnoRepository turnoRepository;
    private final HorarioAtencionRepository horarioAtencionRepository;
    private final MedicoRepository medicoRepository;
    private final PacienteRepository pacienteRepository;
    private final MotivoCancelacionRepository motivoCancelacionRepository;
    private final AfiliacionRepository afiliacionRepository;
    private final DiaNoLaborableRepository diaNoLaborableRepository;
    private final UsuarioContextService usuarioContextService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DisponibilidadResponse disponibilidad(Long medicoId, LocalDate fecha) {
        if (fecha.isBefore(LocalDate.now(clock))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha no puede ser pasada");
        }
        medicoRepository.findById(medicoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Médico no encontrado"));

        // Más allá de la ventana de reserva no se puede reservar: no se ofrecen horarios que después
        // el POST /api/turnos rechazaría con "tanta anticipación".
        if (fecha.isAfter(LocalDate.now(clock).plusDays(VENTANA_RESERVA_DIAS))) {
            return new DisponibilidadResponse(fecha, List.of());
        }

        Optional<String> cierre = avisoDeCierre(medicoId, fecha);
        if (cierre.isPresent()) {
            return new DisponibilidadResponse(fecha, List.of(), cierre.get());
        }

        List<HorarioAtencion> horarios = horariosVigentes(medicoId, fecha);
        if (horarios.isEmpty()) {
            return new DisponibilidadResponse(fecha, List.of());
        }

        // Se comparan intervalos, no horas de inicio: si el horario cambió de duración, un turno
        // viejo de 09:30 a 10:00 tiene que bloquear también un slot nuevo que empiece a las 09:40.
        List<Turno> activos = turnoRepository.findByMedicoIdAndInicioBetweenAndEstadoNot(
                medicoId, fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay(), EstadoTurno.CANCELADO);

        LocalDateTime ahora = LocalDateTime.now(clock);
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
                LocalDateTime finSlot = inicioSlot.plusMinutes(horario.getDuracionTurnoMin());
                if (inicioSlot.isAfter(ahora) && !seSolapaConAlguno(activos, inicioSlot, finSlot)) {
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
        if (inicio.isBefore(LocalDateTime.now(clock))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se puede reservar en el pasado");
        }
        if (fecha.isAfter(LocalDate.now(clock).plusDays(VENTANA_RESERVA_DIAS))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se puede reservar con tanta anticipación");
        }

        if (avisoDeCierre(medico.getId(), fecha).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El médico no atiende ese día");
        }

        controlarTopeDeTurnosPendientes(usuarioActual, paciente);

        HorarioAtencion horario = horariosVigentes(medico.getId(), fecha).stream()
                .filter(h -> encajaEnHorario(inicio.toLocalTime(), h))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El horario elegido no corresponde a la agenda del médico"));

        LocalDateTime fin = inicio.plusMinutes(horario.getDuracionTurnoMin());

        List<Turno> activosDelMedico = turnoRepository.findByMedicoIdAndInicioBetweenAndEstadoNot(
                medico.getId(), fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay(), EstadoTurno.CANCELADO);
        if (seSolapaConAlguno(activosDelMedico, inicio, fin)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese horario ya no está disponible, elegí otro");
        }

        boolean pacienteOcupado = turnoRepository
                .findByPacienteIdAndEstadoNotAndInicioBetween(
                        paciente.getId(), EstadoTurno.CANCELADO, fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay())
                .stream()
                .anyMatch(t -> inicio.isBefore(t.getFin()) && t.getInicio().isBefore(fin));
        if (pacienteOcupado) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya tenés un turno reservado en ese horario");
        }

        Afiliacion afiliacion = resolverAfiliacion(request.afiliacionId(), paciente);

        Turno turno = Turno.builder()
                .paciente(paciente)
                .medico(medico)
                .consultorio(horario.getConsultorio())
                .creadoPor(usuarioActual)
                .inicio(inicio)
                .fin(fin)
                .motivoConsulta(request.motivoConsulta())
                .afiliacion(afiliacion)
                .build();

        try {
            return TurnoResponse.from(turnoRepository.saveAndFlush(turno));
        } catch (DataIntegrityViolationException ex) {
            // Perdió la carrera contra una restricción de la base (V7): otro pedido simultáneo
            // ocupó ese horario del médico, o el mismo paciente reservó otro turno a esa hora.
            throw new ResponseStatusException(HttpStatus.CONFLICT, esChoqueDelPaciente(ex)
                    ? "Ya tenés un turno reservado en ese horario"
                    : "Ese horario ya fue reservado, elegí otro");
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
     * otro); el admin y la recepción pueden ver la de cualquiera, pero tienen que indicar cuál.
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
        } else if (usuarioActual.getRol() == Rol.ADMIN || usuarioActual.getRol() == Rol.RECEPCIONISTA) {
            if (medicoIdParam == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indicá qué médico querés ver");
            }
            medicoId = medicoIdParam;
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un médico o el personal de la clínica puede ver una agenda");
        }

        List<Turno> turnos = fecha != null
                ? turnoRepository.findByMedicoIdAndInicioBetweenOrderByInicioAsc(
                        medicoId, fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay())
                : turnoRepository.findByMedicoIdAndInicioGreaterThanEqualOrderByInicioAsc(
                        medicoId, LocalDate.now(clock).atStartOfDay());
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
            long horasHastaElTurno = Duration.between(LocalDateTime.now(clock), turno.getInicio()).toHours();
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
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el médico del turno o el personal de la clínica puede marcar esto");
        }
        // También un turno que nunca se confirmó: si el paciente no vino, hay que poder cerrarlo.
        if (turno.getEstado() != EstadoTurno.RESERVADO && turno.getEstado() != EstadoTurno.CONFIRMADO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se puede marcar ausente un turno reservado o confirmado");
        }
        if (LocalDateTime.now(clock).isBefore(turno.getInicio().plusMinutes(TOLERANCIA_AUSENCIA_MINUTOS))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Hay que esperar " + TOLERANCIA_AUSENCIA_MINUTOS + " minutos desde la hora del turno antes de marcar la ausencia");
        }

        turno.setEstado(EstadoTurno.AUSENTE);
        return TurnoResponse.from(turnoRepository.save(turno));
    }

    /** Dueño del turno (paciente), médico asignado a él, o el personal de la clínica (admin y recepción). */
    private boolean puedeGestionar(Usuario usuarioActual, Turno turno) {
        return switch (usuarioActual.getRol()) {
            case ADMIN, RECEPCIONISTA -> true;
            case MEDICO -> turno.getMedico().getUsuario().getId().equals(usuarioActual.getId());
            case PACIENTE -> turno.getPaciente().getUsuario().getId().equals(usuarioActual.getId());
        };
    }

    /**
     * El paciente reserva para sí mismo; un médico, la recepción o un admin reservan a nombre
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
        // Solo pacientes con la identidad verificada: un DNI sin verificar lo pudo escribir cualquiera.
        return pacienteRepository.findByDniAndIdentidadVerificadaTrue(pacienteDni)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No hay ningún paciente con la identidad verificada con ese DNI"));
    }

    /** Si se indica, tiene que ser una afiliación del mismo paciente que reserva. */
    private Afiliacion resolverAfiliacion(Long afiliacionId, Paciente paciente) {
        if (afiliacionId == null) {
            return null;
        }
        Afiliacion afiliacion = afiliacionRepository.findById(afiliacionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Afiliación no encontrada"));
        if (!afiliacion.getPaciente().getId().equals(paciente.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Esa afiliación no es del paciente");
        }
        return afiliacion;
    }

    /**
     * Tope de turnos pendientes por paciente. Se toma antes el candado de su fila: sin él, dos
     * reservas simultáneas cuentan las dos "1 pendiente" y el paciente termina con tres.
     */
    private void controlarTopeDeTurnosPendientes(Usuario usuarioActual, Paciente paciente) {
        pacienteRepository.bloquearPorId(paciente.getId());
        long pendientes = turnoRepository.countByPacienteIdAndEstadoInAndFinAfter(
                paciente.getId(), PENDIENTES, LocalDateTime.now(clock));
        if (pendientes >= MAXIMO_TURNOS_PENDIENTES) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, usuarioActual.getRol() == Rol.PACIENTE
                    ? "Ya tenés " + MAXIMO_TURNOS_PENDIENTES + " turnos pendientes, que es el máximo. Cancelá uno o esperá a atenderte para reservar otro"
                    : "Ese paciente ya tiene " + MAXIMO_TURNOS_PENDIENTES + " turnos pendientes, que es el máximo. Hay que cancelar uno antes de reservar otro");
        }
    }

    /**
     * Si ese día está cerrado para el médico (feriado de la clínica o ausencia suya), el texto que
     * se le muestra al paciente. De una ausencia no se cuenta el motivo: puede ser una enfermedad.
     */
    private Optional<String> avisoDeCierre(Long medicoId, LocalDate fecha) {
        List<DiaNoLaborable> cierres = diaNoLaborableRepository.cubrenFecha(medicoId, fecha);
        return cierres.stream()
                .filter(c -> c.getMedico() == null)
                .findFirst()
                .map(c -> "La clínica no atiende ese día: " + c.getDescripcion())
                .or(() -> cierres.isEmpty() ? Optional.empty() : Optional.of("El médico no atiende ese día"));
    }

    private boolean seSolapaConAlguno(List<Turno> turnos, LocalDateTime inicio, LocalDateTime fin) {
        return turnos.stream().anyMatch(t -> inicio.isBefore(t.getFin()) && t.getInicio().isBefore(fin));
    }

    private boolean esChoqueDelPaciente(DataIntegrityViolationException ex) {
        String causa = ex.getMostSpecificCause().getMessage();
        return causa != null && causa.contains("ex_turno_paciente_sin_solapes");
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
