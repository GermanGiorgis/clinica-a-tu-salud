package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.CancelarTurnoRequest;
import com.germanggiorgis.clinica.dto.CrearTurnoRequest;
import com.germanggiorgis.clinica.dto.DisponibilidadResponse;
import com.germanggiorgis.clinica.dto.TurnoResponse;
import com.germanggiorgis.clinica.model.Afiliacion;
import com.germanggiorgis.clinica.model.Consultorio;
import com.germanggiorgis.clinica.model.EstadoTurno;
import com.germanggiorgis.clinica.model.HorarioAtencion;
import com.germanggiorgis.clinica.model.Medico;
import com.germanggiorgis.clinica.model.MotivoCancelacion;
import com.germanggiorgis.clinica.model.ObraSocial;
import com.germanggiorgis.clinica.model.Paciente;
import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Turno;
import com.germanggiorgis.clinica.model.Usuario;
import com.germanggiorgis.clinica.repository.AfiliacionRepository;
import com.germanggiorgis.clinica.repository.HorarioAtencionRepository;
import com.germanggiorgis.clinica.repository.MedicoRepository;
import com.germanggiorgis.clinica.repository.MotivoCancelacionRepository;
import com.germanggiorgis.clinica.repository.PacienteRepository;
import com.germanggiorgis.clinica.repository.TurnoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.time.ZoneId;
import java.util.Optional;

import static com.germanggiorgis.clinica.service.HttpStatusAssert.assertStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * "El corazón del proyecto" (ver el javadoc de TurnoService): acá se prueba
 * el cálculo de disponibilidad y las reglas de reserva/cancelación de la
 * regla 1 a 5 de docs/modelo-datos.md, sin tocar una base de datos real
 * (eso ya lo cubre EntidadesFase1IntegrationTest para la parte de esquema).
 */
@ExtendWith(MockitoExtension.class)
class TurnoServiceTest {

    @Mock
    private TurnoRepository turnoRepository;
    @Mock
    private HorarioAtencionRepository horarioAtencionRepository;
    @Mock
    private MedicoRepository medicoRepository;
    @Mock
    private PacienteRepository pacienteRepository;
    @Mock
    private MotivoCancelacionRepository motivoCancelacionRepository;
    @Mock
    private AfiliacionRepository afiliacionRepository;
    @Mock
    private UsuarioContextService usuarioContextService;
    /** Reloj real por defecto; los tests de zona horaria lo fijan con {@link #ahoraEs}. */
    @Spy
    private Clock clock = Clock.systemDefaultZone();

    @InjectMocks
    private TurnoService turnoService;

    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");

    /** Fija el reloj: el instante UTC dado, visto desde la zona dada. */
    private void ahoraEs(String instanteUtc, ZoneId zona) {
        doReturn(Instant.parse(instanteUtc)).when(clock).instant();
        doReturn(zona).when(clock).getZone();
    }

    private Usuario usuario(long id, Rol rol) {
        return Usuario.builder().id(id).email("u" + id + "@clinica.test").passwordHash("x")
                .rol(rol).nombre("N" + id).apellido("A" + id).activo(true).build();
    }

    private Paciente paciente(long id, Usuario usuario) {
        return Paciente.builder().id(id).usuario(usuario).dni("DNI" + id).fechaNacimiento(LocalDate.of(1990, 1, 1)).build();
    }

    private Medico medico(long id, Usuario usuario) {
        return Medico.builder().id(id).usuario(usuario).matricula("MP-" + id).especialidades(List.of()).build();
    }

    private Consultorio consultorio(long id) {
        return Consultorio.builder().id(id).nombre("Consultorio " + id).build();
    }

    private HorarioAtencion horario(Medico medico, Consultorio consultorio, DayOfWeek dia, LocalTime inicio, LocalTime fin, int duracion) {
        return HorarioAtencion.builder().id(1L).medico(medico).consultorio(consultorio).diaSemana(dia)
                .horaInicio(inicio).horaFin(fin).duracionTurnoMin(duracion)
                .vigenteDesde(LocalDate.now().minusDays(30)).build();
    }

    private Turno turno(long id, Paciente paciente, Medico medico, Consultorio consultorio, LocalDateTime inicio, EstadoTurno estado) {
        return Turno.builder().id(id).paciente(paciente).medico(medico).consultorio(consultorio)
                .creadoPor(paciente.getUsuario()).inicio(inicio).fin(inicio.plusMinutes(30)).estado(estado).build();
    }

    // --- disponibilidad ---

    @Test
    void disponibilidad_rechaza_fecha_pasada() {
        LocalDate ayer = LocalDate.now().minusDays(1);
        assertStatus(() -> turnoService.disponibilidad(1L, ayer), HttpStatus.BAD_REQUEST);
    }

    @Test
    void disponibilidad_rechaza_medico_inexistente() {
        when(medicoRepository.findById(1L)).thenReturn(Optional.empty());
        assertStatus(() -> turnoService.disponibilidad(1L, LocalDate.now()), HttpStatus.NOT_FOUND);
    }

    @Test
    void disponibilidad_sin_horarios_vigentes_devuelve_vacio() {
        Medico medico = medico(1L, usuario(1L, Rol.MEDICO));
        LocalDate fecha = LocalDate.now().plusDays(1);
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico));
        when(horarioAtencionRepository.findByMedicoIdAndDiaSemana(1L, fecha.getDayOfWeek())).thenReturn(List.of());

        DisponibilidadResponse respuesta = turnoService.disponibilidad(1L, fecha);

        assertThat(respuesta.horariosLibres()).isEmpty();
    }

    @Test
    void disponibilidad_calcula_slots_libres_sin_ocupados() {
        Medico medico = medico(1L, usuario(1L, Rol.MEDICO));
        Consultorio consultorio = consultorio(1L);
        LocalDate fecha = LocalDate.now().plusDays(1);
        HorarioAtencion horario = horario(medico, consultorio, fecha.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(11, 0), 30);

        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico));
        when(horarioAtencionRepository.findByMedicoIdAndDiaSemana(1L, fecha.getDayOfWeek())).thenReturn(List.of(horario));
        when(turnoRepository.findByMedicoIdAndInicioBetweenAndEstadoNot(eq(1L), any(), any(), eq(EstadoTurno.CANCELADO)))
                .thenReturn(List.of());

        DisponibilidadResponse respuesta = turnoService.disponibilidad(1L, fecha);

        assertThat(respuesta.horariosLibres())
                .containsExactly(LocalTime.of(9, 0), LocalTime.of(9, 30), LocalTime.of(10, 0), LocalTime.of(10, 30));
    }

    @Test
    void disponibilidad_excluye_horarios_ya_ocupados() {
        Medico medico = medico(1L, usuario(1L, Rol.MEDICO));
        Consultorio consultorio = consultorio(1L);
        LocalDate fecha = LocalDate.now().plusDays(1);
        HorarioAtencion horario = horario(medico, consultorio, fecha.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(10, 0), 30);
        Turno ocupado = turno(1L, paciente(1L, usuario(2L, Rol.PACIENTE)), medico, consultorio,
                LocalDateTime.of(fecha, LocalTime.of(9, 30)), EstadoTurno.RESERVADO);

        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico));
        when(horarioAtencionRepository.findByMedicoIdAndDiaSemana(1L, fecha.getDayOfWeek())).thenReturn(List.of(horario));
        when(turnoRepository.findByMedicoIdAndInicioBetweenAndEstadoNot(eq(1L), any(), any(), eq(EstadoTurno.CANCELADO)))
                .thenReturn(List.of(ocupado));

        DisponibilidadResponse respuesta = turnoService.disponibilidad(1L, fecha);

        assertThat(respuesta.horariosLibres()).containsExactly(LocalTime.of(9, 0));
    }

    @Test
    void disponibilidad_excluye_horarios_que_ya_pasaron_hoy() {
        Medico medico = medico(1L, usuario(1L, Rol.MEDICO));
        Consultorio consultorio = consultorio(1L);
        LocalDate hoy = LocalDate.now();
        LocalTime finYaPasado = LocalTime.now().minusMinutes(1);
        LocalTime inicioYaPasado = finYaPasado.minusHours(1);
        HorarioAtencion horario = horario(medico, consultorio, hoy.getDayOfWeek(), inicioYaPasado, finYaPasado, 60);

        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico));
        when(horarioAtencionRepository.findByMedicoIdAndDiaSemana(1L, hoy.getDayOfWeek())).thenReturn(List.of(horario));
        when(turnoRepository.findByMedicoIdAndInicioBetweenAndEstadoNot(eq(1L), any(), any(), eq(EstadoTurno.CANCELADO)))
                .thenReturn(List.of());

        DisponibilidadResponse respuesta = turnoService.disponibilidad(1L, hoy);

        assertThat(respuesta.horariosLibres()).isEmpty();
    }

    // --- crear ---

    @Test
    void crear_reserva_para_si_mismo_como_paciente() {
        Usuario usuarioPaciente = usuario(2L, Rol.PACIENTE);
        Paciente paciente = paciente(1L, usuarioPaciente);
        Medico medico = medico(1L, usuario(1L, Rol.MEDICO));
        Consultorio consultorio = consultorio(1L);
        LocalDate fecha = LocalDate.now().plusDays(1);
        LocalDateTime inicio = LocalDateTime.of(fecha, LocalTime.of(9, 0));
        HorarioAtencion horario = horario(medico, consultorio, fecha.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(12, 0), 30);

        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente);
        when(pacienteRepository.findByUsuarioId(2L)).thenReturn(Optional.of(paciente));
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico));
        when(horarioAtencionRepository.findByMedicoIdAndDiaSemana(1L, fecha.getDayOfWeek())).thenReturn(List.of(horario));
        when(turnoRepository.findByPacienteIdAndEstadoNotAndInicioBetween(eq(1L), eq(EstadoTurno.CANCELADO), any(), any()))
                .thenReturn(List.of());
        when(turnoRepository.saveAndFlush(any(Turno.class))).thenAnswer(inv -> {
            Turno t = inv.getArgument(0);
            t.setId(99L);
            return t;
        });

        CrearTurnoRequest request = new CrearTurnoRequest(1L, inicio, "Motivo", null, null);
        TurnoResponse respuesta = turnoService.crear(request);

        assertThat(respuesta.id()).isEqualTo(99L);
        assertThat(respuesta.estado()).isEqualTo(EstadoTurno.RESERVADO);
        assertThat(respuesta.obraSocialNombre()).isNull();
    }

    @Test
    void crear_rechaza_reserva_en_el_pasado() {
        Usuario usuarioPaciente = usuario(2L, Rol.PACIENTE);
        Paciente paciente = paciente(1L, usuarioPaciente);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente);
        when(pacienteRepository.findByUsuarioId(2L)).thenReturn(Optional.of(paciente));
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico(1L, usuario(1L, Rol.MEDICO))));

        CrearTurnoRequest request = new CrearTurnoRequest(1L, LocalDateTime.now().minusHours(1), null, null, null);
        assertStatus(() -> turnoService.crear(request), HttpStatus.BAD_REQUEST);
    }

    @Test
    void crear_rechaza_reserva_fuera_de_la_ventana() {
        Usuario usuarioPaciente = usuario(2L, Rol.PACIENTE);
        Paciente paciente = paciente(1L, usuarioPaciente);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente);
        when(pacienteRepository.findByUsuarioId(2L)).thenReturn(Optional.of(paciente));
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico(1L, usuario(1L, Rol.MEDICO))));

        LocalDateTime inicio = LocalDateTime.of(LocalDate.now().plusDays(61), LocalTime.of(9, 0));
        CrearTurnoRequest request = new CrearTurnoRequest(1L, inicio, null, null, null);
        assertStatus(() -> turnoService.crear(request), HttpStatus.BAD_REQUEST);
    }

    @Test
    void crear_rechaza_horario_que_no_coincide_con_la_agenda() {
        Usuario usuarioPaciente = usuario(2L, Rol.PACIENTE);
        Paciente paciente = paciente(1L, usuarioPaciente);
        Medico medico = medico(1L, usuario(1L, Rol.MEDICO));
        Consultorio consultorio = consultorio(1L);
        LocalDate fecha = LocalDate.now().plusDays(1);
        HorarioAtencion horario = horario(medico, consultorio, fecha.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(12, 0), 30);

        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente);
        when(pacienteRepository.findByUsuarioId(2L)).thenReturn(Optional.of(paciente));
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico));
        when(horarioAtencionRepository.findByMedicoIdAndDiaSemana(1L, fecha.getDayOfWeek())).thenReturn(List.of(horario));

        // 09:15 no cae en la grilla de 30 minutos que arranca en las 09:00
        LocalDateTime inicio = LocalDateTime.of(fecha, LocalTime.of(9, 15));
        CrearTurnoRequest request = new CrearTurnoRequest(1L, inicio, null, null, null);
        assertStatus(() -> turnoService.crear(request), HttpStatus.BAD_REQUEST);
    }

    @Test
    void crear_rechaza_si_el_paciente_ya_tiene_un_turno_superpuesto() {
        Usuario usuarioPaciente = usuario(2L, Rol.PACIENTE);
        Paciente paciente = paciente(1L, usuarioPaciente);
        Medico medico = medico(1L, usuario(1L, Rol.MEDICO));
        Consultorio consultorio = consultorio(1L);
        LocalDate fecha = LocalDate.now().plusDays(1);
        LocalDateTime inicio = LocalDateTime.of(fecha, LocalTime.of(9, 0));
        HorarioAtencion horario = horario(medico, consultorio, fecha.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(12, 0), 30);
        Turno existente = turno(5L, paciente, medico, consultorio, inicio, EstadoTurno.RESERVADO);

        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente);
        when(pacienteRepository.findByUsuarioId(2L)).thenReturn(Optional.of(paciente));
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico));
        when(horarioAtencionRepository.findByMedicoIdAndDiaSemana(1L, fecha.getDayOfWeek())).thenReturn(List.of(horario));
        when(turnoRepository.findByPacienteIdAndEstadoNotAndInicioBetween(eq(1L), eq(EstadoTurno.CANCELADO), any(), any()))
                .thenReturn(List.of(existente));

        CrearTurnoRequest request = new CrearTurnoRequest(1L, inicio, null, null, null);
        assertStatus(() -> turnoService.crear(request), HttpStatus.CONFLICT);
    }

    @Test
    void crear_traduce_la_carrera_contra_el_indice_unico_en_409() {
        Usuario usuarioPaciente = usuario(2L, Rol.PACIENTE);
        Paciente paciente = paciente(1L, usuarioPaciente);
        Medico medico = medico(1L, usuario(1L, Rol.MEDICO));
        Consultorio consultorio = consultorio(1L);
        LocalDate fecha = LocalDate.now().plusDays(1);
        LocalDateTime inicio = LocalDateTime.of(fecha, LocalTime.of(9, 0));
        HorarioAtencion horario = horario(medico, consultorio, fecha.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(12, 0), 30);

        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente);
        when(pacienteRepository.findByUsuarioId(2L)).thenReturn(Optional.of(paciente));
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico));
        when(horarioAtencionRepository.findByMedicoIdAndDiaSemana(1L, fecha.getDayOfWeek())).thenReturn(List.of(horario));
        when(turnoRepository.findByPacienteIdAndEstadoNotAndInicioBetween(eq(1L), eq(EstadoTurno.CANCELADO), any(), any()))
                .thenReturn(List.of());
        when(turnoRepository.saveAndFlush(any(Turno.class))).thenThrow(new DataIntegrityViolationException("duplicado"));

        CrearTurnoRequest request = new CrearTurnoRequest(1L, inicio, null, null, null);
        assertStatus(() -> turnoService.crear(request), HttpStatus.CONFLICT);
    }

    @Test
    void disponibilidad_bloquea_un_slot_que_se_solapa_con_un_turno_aunque_empiecen_a_horas_distintas() {
        // Caso real: el horario cambió de 30 a 20 minutos y quedó un turno viejo de 09:10 a 09:40.
        Medico medico = medico(1L, usuario(1L, Rol.MEDICO));
        Consultorio consultorio = consultorio(1L);
        LocalDate fecha = LocalDate.now().plusDays(1);
        HorarioAtencion horario = horario(medico, consultorio, fecha.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(10, 0), 20);
        Turno viejo = turno(1L, paciente(1L, usuario(2L, Rol.PACIENTE)), medico, consultorio,
                LocalDateTime.of(fecha, LocalTime.of(9, 10)), EstadoTurno.RESERVADO);
        viejo.setFin(LocalDateTime.of(fecha, LocalTime.of(9, 40)));

        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico));
        when(horarioAtencionRepository.findByMedicoIdAndDiaSemana(1L, fecha.getDayOfWeek())).thenReturn(List.of(horario));
        when(turnoRepository.findByMedicoIdAndInicioBetweenAndEstadoNot(eq(1L), any(), any(), eq(EstadoTurno.CANCELADO)))
                .thenReturn(List.of(viejo));

        DisponibilidadResponse respuesta = turnoService.disponibilidad(1L, fecha);

        // 09:00-09:20 y 09:20-09:40 pisan al turno; 09:40-10:00 empieza justo cuando termina.
        assertThat(respuesta.horariosLibres()).containsExactly(LocalTime.of(9, 40));
    }

    @Test
    void crear_rechaza_un_horario_que_se_solapa_con_un_turno_del_medico_aunque_no_empiece_igual() {
        Usuario usuarioPaciente = usuario(2L, Rol.PACIENTE);
        Paciente paciente = paciente(1L, usuarioPaciente);
        Medico medico = medico(1L, usuario(1L, Rol.MEDICO));
        Consultorio consultorio = consultorio(1L);
        LocalDate fecha = LocalDate.now().plusDays(1);
        HorarioAtencion horario = horario(medico, consultorio, fecha.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(12, 0), 20);
        // Turno viejo de 09:30 a 10:00 (de cuando el horario era de 30 minutos).
        Turno viejo = turno(5L, paciente(9L, usuario(9L, Rol.PACIENTE)), medico, consultorio,
                LocalDateTime.of(fecha, LocalTime.of(9, 30)), EstadoTurno.RESERVADO);

        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente);
        when(pacienteRepository.findByUsuarioId(2L)).thenReturn(Optional.of(paciente));
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico));
        when(horarioAtencionRepository.findByMedicoIdAndDiaSemana(1L, fecha.getDayOfWeek())).thenReturn(List.of(horario));
        when(turnoRepository.findByMedicoIdAndInicioBetweenAndEstadoNot(eq(1L), any(), any(), eq(EstadoTurno.CANCELADO)))
                .thenReturn(List.of(viejo));

        CrearTurnoRequest request = new CrearTurnoRequest(1L, LocalDateTime.of(fecha, LocalTime.of(9, 40)), null, null, null);

        assertStatus(() -> turnoService.crear(request), HttpStatus.CONFLICT);
        verify(turnoRepository, never()).saveAndFlush(any(Turno.class));
    }

    @Test
    void crear_distingue_en_el_mensaje_si_choco_el_horario_del_medico_o_el_del_paciente() {
        Usuario usuarioPaciente = usuario(2L, Rol.PACIENTE);
        Paciente paciente = paciente(1L, usuarioPaciente);
        Medico medico = medico(1L, usuario(1L, Rol.MEDICO));
        LocalDate fecha = LocalDate.now().plusDays(1);
        LocalDateTime inicio = LocalDateTime.of(fecha, LocalTime.of(9, 0));
        HorarioAtencion horario = horario(medico, consultorio(1L), fecha.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(12, 0), 30);

        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente);
        when(pacienteRepository.findByUsuarioId(2L)).thenReturn(Optional.of(paciente));
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico));
        when(horarioAtencionRepository.findByMedicoIdAndDiaSemana(1L, fecha.getDayOfWeek())).thenReturn(List.of(horario));
        when(turnoRepository.saveAndFlush(any(Turno.class)))
                .thenThrow(new DataIntegrityViolationException("x", new RuntimeException("violates exclusion constraint \"ex_turno_paciente_sin_solapes\"")))
                .thenThrow(new DataIntegrityViolationException("x", new RuntimeException("violates exclusion constraint \"ex_turno_medico_sin_solapes\"")));

        CrearTurnoRequest request = new CrearTurnoRequest(1L, inicio, null, null, null);

        assertThatThrownBy(() -> turnoService.crear(request))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Ya tenés un turno reservado en ese horario");
        assertThatThrownBy(() -> turnoService.crear(request))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Ese horario ya fue reservado");
    }

    @Test
    void crear_permite_a_un_medico_reservar_a_nombre_de_un_paciente_por_dni() {
        Usuario usuarioMedicoActor = usuario(10L, Rol.MEDICO);
        Usuario usuarioPacienteDestino = usuario(2L, Rol.PACIENTE);
        Paciente paciente = paciente(1L, usuarioPacienteDestino);
        Medico medico = medico(1L, usuario(1L, Rol.MEDICO));
        Consultorio consultorio = consultorio(1L);
        LocalDate fecha = LocalDate.now().plusDays(1);
        LocalDateTime inicio = LocalDateTime.of(fecha, LocalTime.of(9, 0));
        HorarioAtencion horario = horario(medico, consultorio, fecha.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(12, 0), 30);

        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioMedicoActor);
        when(pacienteRepository.findByDniAndIdentidadVerificadaTrue("30111222")).thenReturn(Optional.of(paciente));
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico));
        when(horarioAtencionRepository.findByMedicoIdAndDiaSemana(1L, fecha.getDayOfWeek())).thenReturn(List.of(horario));
        when(turnoRepository.findByPacienteIdAndEstadoNotAndInicioBetween(eq(1L), eq(EstadoTurno.CANCELADO), any(), any()))
                .thenReturn(List.of());
        when(turnoRepository.saveAndFlush(any(Turno.class))).thenAnswer(inv -> inv.getArgument(0));

        CrearTurnoRequest request = new CrearTurnoRequest(1L, inicio, null, "30111222", null);
        TurnoResponse respuesta = turnoService.crear(request);

        assertThat(respuesta.pacienteId()).isEqualTo(1L);
    }

    @Test
    void crear_rechaza_a_un_medico_que_no_indica_dni_del_paciente() {
        Usuario usuarioMedicoActor = usuario(10L, Rol.MEDICO);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioMedicoActor);

        CrearTurnoRequest request = new CrearTurnoRequest(1L, LocalDateTime.now().plusDays(1), null, null, null);
        assertStatus(() -> turnoService.crear(request), HttpStatus.BAD_REQUEST);
    }

    @Test
    void crear_rechaza_dni_de_paciente_inexistente() {
        Usuario usuarioAdminActor = usuario(10L, Rol.ADMIN);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioAdminActor);
        when(pacienteRepository.findByDniAndIdentidadVerificadaTrue("00000000")).thenReturn(Optional.empty());

        CrearTurnoRequest request = new CrearTurnoRequest(1L, LocalDateTime.now().plusDays(1), null, "00000000", null);
        assertStatus(() -> turnoService.crear(request), HttpStatus.NOT_FOUND);
    }

    @Test
    void crear_ignora_el_dni_cuando_reserva_un_paciente() {
        // Un paciente que mande pacienteDni en el body (no debería, pero si lo hace)
        // reserva igual para sí mismo: el DNI ajeno no se usa.
        Usuario usuarioPaciente = usuario(2L, Rol.PACIENTE);
        Paciente paciente = paciente(1L, usuarioPaciente);
        Medico medico = medico(1L, usuario(1L, Rol.MEDICO));
        Consultorio consultorio = consultorio(1L);
        LocalDate fecha = LocalDate.now().plusDays(1);
        LocalDateTime inicio = LocalDateTime.of(fecha, LocalTime.of(9, 0));
        HorarioAtencion horario = horario(medico, consultorio, fecha.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(12, 0), 30);

        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente);
        when(pacienteRepository.findByUsuarioId(2L)).thenReturn(Optional.of(paciente));
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico));
        when(horarioAtencionRepository.findByMedicoIdAndDiaSemana(1L, fecha.getDayOfWeek())).thenReturn(List.of(horario));
        when(turnoRepository.findByPacienteIdAndEstadoNotAndInicioBetween(eq(1L), eq(EstadoTurno.CANCELADO), any(), any()))
                .thenReturn(List.of());
        when(turnoRepository.saveAndFlush(any(Turno.class))).thenAnswer(inv -> inv.getArgument(0));

        CrearTurnoRequest request = new CrearTurnoRequest(1L, inicio, null, "99999999", null);
        TurnoResponse respuesta = turnoService.crear(request);

        assertThat(respuesta.pacienteId()).isEqualTo(1L);
    }

    @Test
    void crear_acepta_una_afiliacion_propia_del_paciente() {
        Usuario usuarioPaciente = usuario(2L, Rol.PACIENTE);
        Paciente paciente = paciente(1L, usuarioPaciente);
        Medico medico = medico(1L, usuario(1L, Rol.MEDICO));
        Consultorio consultorio = consultorio(1L);
        LocalDate fecha = LocalDate.now().plusDays(1);
        LocalDateTime inicio = LocalDateTime.of(fecha, LocalTime.of(9, 0));
        HorarioAtencion horario = horario(medico, consultorio, fecha.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(12, 0), 30);
        ObraSocial obraSocial = ObraSocial.builder().id(1L).nombre("OSDE").build();
        Afiliacion afiliacion = Afiliacion.builder().id(7L).paciente(paciente).obraSocial(obraSocial)
                .numeroAfiliado("123").vigenteDesde(LocalDate.now().minusYears(1)).build();

        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente);
        when(pacienteRepository.findByUsuarioId(2L)).thenReturn(Optional.of(paciente));
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico));
        when(horarioAtencionRepository.findByMedicoIdAndDiaSemana(1L, fecha.getDayOfWeek())).thenReturn(List.of(horario));
        when(turnoRepository.findByPacienteIdAndEstadoNotAndInicioBetween(eq(1L), eq(EstadoTurno.CANCELADO), any(), any()))
                .thenReturn(List.of());
        when(afiliacionRepository.findById(7L)).thenReturn(Optional.of(afiliacion));
        when(turnoRepository.saveAndFlush(any(Turno.class))).thenAnswer(inv -> inv.getArgument(0));

        CrearTurnoRequest request = new CrearTurnoRequest(1L, inicio, null, null, 7L);
        TurnoResponse respuesta = turnoService.crear(request);

        assertThat(respuesta.obraSocialNombre()).isEqualTo("OSDE");
    }

    @Test
    void crear_rechaza_una_afiliacion_de_otro_paciente() {
        Usuario usuarioPaciente = usuario(2L, Rol.PACIENTE);
        Paciente paciente = paciente(1L, usuarioPaciente);
        Paciente otroPaciente = paciente(2L, usuario(3L, Rol.PACIENTE));
        Medico medico = medico(1L, usuario(1L, Rol.MEDICO));
        Consultorio consultorio = consultorio(1L);
        LocalDate fecha = LocalDate.now().plusDays(1);
        LocalDateTime inicio = LocalDateTime.of(fecha, LocalTime.of(9, 0));
        HorarioAtencion horario = horario(medico, consultorio, fecha.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(12, 0), 30);
        Afiliacion afiliacionAjena = Afiliacion.builder().id(7L).paciente(otroPaciente)
                .obraSocial(ObraSocial.builder().id(1L).nombre("OSDE").build())
                .numeroAfiliado("123").vigenteDesde(LocalDate.now().minusYears(1)).build();

        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente);
        when(pacienteRepository.findByUsuarioId(2L)).thenReturn(Optional.of(paciente));
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico));
        when(horarioAtencionRepository.findByMedicoIdAndDiaSemana(1L, fecha.getDayOfWeek())).thenReturn(List.of(horario));
        when(turnoRepository.findByPacienteIdAndEstadoNotAndInicioBetween(eq(1L), eq(EstadoTurno.CANCELADO), any(), any()))
                .thenReturn(List.of());
        when(afiliacionRepository.findById(7L)).thenReturn(Optional.of(afiliacionAjena));

        CrearTurnoRequest request = new CrearTurnoRequest(1L, inicio, null, null, 7L);
        assertStatus(() -> turnoService.crear(request), HttpStatus.BAD_REQUEST);
    }

    // --- misTurnos ---

    @Test
    void misTurnos_devuelve_los_turnos_del_paciente_logueado() {
        Usuario usuarioPaciente = usuario(2L, Rol.PACIENTE);
        Paciente paciente = paciente(1L, usuarioPaciente);
        Turno turno = turno(5L, paciente, medico(1L, usuario(1L, Rol.MEDICO)), consultorio(1L),
                LocalDateTime.now().plusDays(1), EstadoTurno.RESERVADO);

        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente);
        when(pacienteRepository.findByUsuarioId(2L)).thenReturn(Optional.of(paciente));
        when(turnoRepository.findByPacienteIdOrderByInicioDesc(1L)).thenReturn(List.of(turno));

        assertThat(turnoService.misTurnos()).hasSize(1);
    }

    // --- agenda ---

    @Test
    void agenda_medico_ve_la_propia_sin_indicar_id() {
        Usuario usuarioMedico = usuario(1L, Rol.MEDICO);
        Medico medico = medico(1L, usuarioMedico);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioMedico);
        when(medicoRepository.findByUsuarioId(1L)).thenReturn(Optional.of(medico));
        when(turnoRepository.findByMedicoIdAndInicioGreaterThanEqualOrderByInicioAsc(eq(1L), any())).thenReturn(List.of());

        assertThat(turnoService.agenda(null, null)).isEmpty();
    }

    @Test
    void agenda_medico_no_puede_pedir_la_de_otro() {
        Usuario usuarioMedico = usuario(1L, Rol.MEDICO);
        Medico medico = medico(1L, usuarioMedico);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioMedico);
        when(medicoRepository.findByUsuarioId(1L)).thenReturn(Optional.of(medico));

        assertStatus(() -> turnoService.agenda(999L, null), HttpStatus.FORBIDDEN);
    }

    @Test
    void agenda_admin_debe_indicar_medicoId() {
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuario(1L, Rol.ADMIN));
        assertStatus(() -> turnoService.agenda(null, null), HttpStatus.BAD_REQUEST);
    }

    @Test
    void agenda_admin_puede_ver_la_de_cualquier_medico_con_fecha() {
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuario(1L, Rol.ADMIN));
        LocalDate fecha = LocalDate.now();
        when(turnoRepository.findByMedicoIdAndInicioBetweenOrderByInicioAsc(eq(5L), any(), any())).thenReturn(List.of());

        assertThat(turnoService.agenda(5L, fecha)).isEmpty();
    }

    @Test
    void agenda_paciente_no_puede_verla() {
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuario(1L, Rol.PACIENTE));
        assertStatus(() -> turnoService.agenda(null, null), HttpStatus.FORBIDDEN);
    }

    // --- cancelar ---

    @Test
    void cancelar_lo_permite_al_dueno_con_anticipacion_suficiente() {
        Usuario usuarioPaciente = usuario(2L, Rol.PACIENTE);
        Paciente paciente = paciente(1L, usuarioPaciente);
        Turno turno = turno(5L, paciente, medico(1L, usuario(1L, Rol.MEDICO)), consultorio(1L),
                LocalDateTime.now().plusHours(5), EstadoTurno.RESERVADO);
        MotivoCancelacion motivo = MotivoCancelacion.builder().id(1L).nombre("No puede asistir").build();

        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente);
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));
        when(motivoCancelacionRepository.findById(1L)).thenReturn(Optional.of(motivo));
        when(turnoRepository.save(any(Turno.class))).thenAnswer(inv -> inv.getArgument(0));

        TurnoResponse respuesta = turnoService.cancelar(5L, new CancelarTurnoRequest(1L));

        assertThat(respuesta.estado()).isEqualTo(EstadoTurno.CANCELADO);
        assertThat(respuesta.motivoCancelacionNombre()).isEqualTo("No puede asistir");
    }

    // --- la ventana de reserva es de 60 días, también para lo que se ofrece ---

    @Test
    void disponibilidad_no_ofrece_horarios_mas_alla_de_la_ventana_de_reserva() {
        Medico medico = medico(1L, usuario(1L, Rol.MEDICO));
        LocalDate fechaFueraDeVentana = LocalDate.now().plusDays(61);
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico));

        DisponibilidadResponse respuesta = turnoService.disponibilidad(1L, fechaFueraDeVentana);

        assertThat(respuesta.horariosLibres()).isEmpty();
        verify(horarioAtencionRepository, never()).findByMedicoIdAndDiaSemana(any(), any());
    }

    @Test
    void disponibilidad_si_ofrece_el_ultimo_dia_de_la_ventana() {
        Medico medico = medico(1L, usuario(1L, Rol.MEDICO));
        LocalDate ultimoDia = LocalDate.now().plusDays(60);
        HorarioAtencion horario = horario(medico, consultorio(1L), ultimoDia.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(10, 0), 30);
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico));
        when(horarioAtencionRepository.findByMedicoIdAndDiaSemana(1L, ultimoDia.getDayOfWeek())).thenReturn(List.of(horario));

        assertThat(turnoService.disponibilidad(1L, ultimoDia).horariosLibres()).containsExactly(LocalTime.of(9, 0), LocalTime.of(9, 30));
    }

    // --- la hora la define la zona de la clínica, no la del servidor ---

    @Test
    void disponibilidad_usa_la_hora_de_la_clinica_para_saber_que_horarios_ya_pasaron() {
        // 15:00 en Buenos Aires (18:00 UTC) del jueves 10/01/2030
        ahoraEs("2030-01-10T18:00:00Z", BUENOS_AIRES);
        Medico medico = medico(1L, usuario(1L, Rol.MEDICO));
        LocalDate fecha = LocalDate.of(2030, 1, 10);
        HorarioAtencion horario = horario(medico, consultorio(1L), fecha.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(18, 0), 30);

        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico));
        when(horarioAtencionRepository.findByMedicoIdAndDiaSemana(1L, fecha.getDayOfWeek())).thenReturn(List.of(horario));

        DisponibilidadResponse respuesta = turnoService.disponibilidad(1L, fecha);

        assertThat(respuesta.horariosLibres()).first().isEqualTo(LocalTime.of(15, 30));
        assertThat(respuesta.horariosLibres()).last().isEqualTo(LocalTime.of(17, 30));
    }

    @Test
    void el_dia_de_hoy_es_el_de_la_clinica_aunque_en_UTC_ya_sea_manana() {
        // A las 23:00 del 09/01 en Buenos Aires ya son las 02:00 del 10/01 en UTC.
        ahoraEs("2030-01-10T02:00:00Z", BUENOS_AIRES);
        Medico medico = medico(1L, usuario(1L, Rol.MEDICO));
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico));
        when(horarioAtencionRepository.findByMedicoIdAndDiaSemana(any(), any())).thenReturn(List.of());

        // El 09/01 todavía es "hoy" para la clínica: no es una fecha pasada.
        assertThat(turnoService.disponibilidad(1L, LocalDate.of(2030, 1, 9)).horariosLibres()).isEmpty();
    }

    @Test
    void con_la_zona_del_servidor_en_UTC_ese_mismo_dia_se_rechazaria_como_pasado() {
        // Es el bug que evita el Clock: el mismo instante visto desde UTC cambia la respuesta.
        ahoraEs("2030-01-10T02:00:00Z", ZoneId.of("UTC"));

        assertStatus(() -> turnoService.disponibilidad(1L, LocalDate.of(2030, 1, 9)), HttpStatus.BAD_REQUEST);
    }

    @Test
    void cancelar_mide_las_dos_horas_de_anticipacion_con_el_reloj_de_la_clinica() {
        Usuario usuarioPaciente = usuario(2L, Rol.PACIENTE);
        Paciente paciente = paciente(1L, usuarioPaciente);
        // Turno a las 16:00 de Buenos Aires.
        Turno turno = turno(5L, paciente, medico(1L, usuario(1L, Rol.MEDICO)), consultorio(1L),
                LocalDateTime.of(2030, 1, 10, 16, 0), EstadoTurno.RESERVADO);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente);
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));

        // 15:00 en Buenos Aires: falta 1 hora.
        ahoraEs("2030-01-10T18:00:00Z", BUENOS_AIRES);

        assertStatus(() -> turnoService.cancelar(5L, new CancelarTurnoRequest(1L)), HttpStatus.BAD_REQUEST);
    }

    @Test
    void cancelar_rechaza_al_dueno_sin_anticipacion_minima() {
        Usuario usuarioPaciente = usuario(2L, Rol.PACIENTE);
        Paciente paciente = paciente(1L, usuarioPaciente);
        Turno turno = turno(5L, paciente, medico(1L, usuario(1L, Rol.MEDICO)), consultorio(1L),
                LocalDateTime.now().plusMinutes(30), EstadoTurno.RESERVADO);

        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente);
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));

        assertStatus(() -> turnoService.cancelar(5L, new CancelarTurnoRequest(1L)), HttpStatus.BAD_REQUEST);
    }

    @Test
    void cancelar_rechaza_a_un_paciente_que_no_es_dueno() {
        Paciente dueno = paciente(1L, usuario(2L, Rol.PACIENTE));
        Usuario otroPaciente = usuario(3L, Rol.PACIENTE);
        Turno turno = turno(5L, dueno, medico(1L, usuario(1L, Rol.MEDICO)), consultorio(1L),
                LocalDateTime.now().plusHours(5), EstadoTurno.RESERVADO);

        when(usuarioContextService.getUsuarioActual()).thenReturn(otroPaciente);
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));

        assertStatus(() -> turnoService.cancelar(5L, new CancelarTurnoRequest(1L)), HttpStatus.FORBIDDEN);
    }

    @Test
    void cancelar_lo_permite_al_medico_del_turno_sin_importar_la_anticipacion() {
        Usuario usuarioMedico = usuario(1L, Rol.MEDICO);
        Medico medico = medico(1L, usuarioMedico);
        Turno turno = turno(5L, paciente(1L, usuario(2L, Rol.PACIENTE)), medico, consultorio(1L),
                LocalDateTime.now().plusMinutes(10), EstadoTurno.CONFIRMADO);
        MotivoCancelacion motivo = MotivoCancelacion.builder().id(1L).nombre("Motivo").build();

        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioMedico);
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));
        when(motivoCancelacionRepository.findById(1L)).thenReturn(Optional.of(motivo));
        when(turnoRepository.save(any(Turno.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(turnoService.cancelar(5L, new CancelarTurnoRequest(1L)).estado()).isEqualTo(EstadoTurno.CANCELADO);
    }

    @Test
    void cancelar_rechaza_al_medico_de_otro_turno() {
        Usuario usuarioMedico = usuario(1L, Rol.MEDICO);
        Medico otroMedico = medico(2L, usuario(99L, Rol.MEDICO));
        Turno turno = turno(5L, paciente(1L, usuario(2L, Rol.PACIENTE)), otroMedico, consultorio(1L),
                LocalDateTime.now().plusHours(5), EstadoTurno.RESERVADO);

        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioMedico);
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));

        assertStatus(() -> turnoService.cancelar(5L, new CancelarTurnoRequest(1L)), HttpStatus.FORBIDDEN);
    }

    @Test
    void cancelar_lo_permite_siempre_a_un_admin() {
        Usuario usuarioAdmin = usuario(1L, Rol.ADMIN);
        Turno turno = turno(5L, paciente(1L, usuario(2L, Rol.PACIENTE)), medico(1L, usuario(3L, Rol.MEDICO)), consultorio(1L),
                LocalDateTime.now().plusMinutes(5), EstadoTurno.CONFIRMADO);
        MotivoCancelacion motivo = MotivoCancelacion.builder().id(1L).nombre("Motivo").build();

        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioAdmin);
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));
        when(motivoCancelacionRepository.findById(1L)).thenReturn(Optional.of(motivo));
        when(turnoRepository.save(any(Turno.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(turnoService.cancelar(5L, new CancelarTurnoRequest(1L)).estado()).isEqualTo(EstadoTurno.CANCELADO);
    }

    @Test
    void cancelar_rechaza_un_turno_que_ya_no_se_puede_cancelar() {
        Usuario usuarioAdmin = usuario(1L, Rol.ADMIN);
        Turno turno = turno(5L, paciente(1L, usuario(2L, Rol.PACIENTE)), medico(1L, usuario(3L, Rol.MEDICO)), consultorio(1L),
                LocalDateTime.now().plusDays(1), EstadoTurno.ATENDIDO);

        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioAdmin);
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));

        assertStatus(() -> turnoService.cancelar(5L, new CancelarTurnoRequest(1L)), HttpStatus.BAD_REQUEST);
    }

    @Test
    void cancelar_rechaza_un_motivo_inexistente() {
        Usuario usuarioAdmin = usuario(1L, Rol.ADMIN);
        Turno turno = turno(5L, paciente(1L, usuario(2L, Rol.PACIENTE)), medico(1L, usuario(3L, Rol.MEDICO)), consultorio(1L),
                LocalDateTime.now().plusDays(1), EstadoTurno.RESERVADO);

        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioAdmin);
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));
        when(motivoCancelacionRepository.findById(1L)).thenReturn(Optional.empty());

        assertStatus(() -> turnoService.cancelar(5L, new CancelarTurnoRequest(1L)), HttpStatus.NOT_FOUND);
    }

    // --- confirmar ---

    @Test
    void confirmar_pasa_de_reservado_a_confirmado() {
        Usuario usuarioPaciente = usuario(2L, Rol.PACIENTE);
        Paciente paciente = paciente(1L, usuarioPaciente);
        Turno turno = turno(5L, paciente, medico(1L, usuario(1L, Rol.MEDICO)), consultorio(1L),
                LocalDateTime.now().plusDays(1), EstadoTurno.RESERVADO);

        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente);
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));
        when(turnoRepository.save(any(Turno.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(turnoService.confirmar(5L).estado()).isEqualTo(EstadoTurno.CONFIRMADO);
    }

    @Test
    void confirmar_rechaza_a_quien_no_puede_gestionar_el_turno() {
        Turno turno = turno(5L, paciente(1L, usuario(2L, Rol.PACIENTE)), medico(1L, usuario(1L, Rol.MEDICO)), consultorio(1L),
                LocalDateTime.now().plusDays(1), EstadoTurno.RESERVADO);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuario(999L, Rol.PACIENTE));
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));

        assertStatus(() -> turnoService.confirmar(5L), HttpStatus.FORBIDDEN);
    }

    @Test
    void confirmar_rechaza_un_turno_que_no_esta_reservado() {
        Usuario usuarioAdmin = usuario(1L, Rol.ADMIN);
        Turno turno = turno(5L, paciente(1L, usuario(2L, Rol.PACIENTE)), medico(1L, usuario(3L, Rol.MEDICO)), consultorio(1L),
                LocalDateTime.now().plusDays(1), EstadoTurno.CONFIRMADO);

        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioAdmin);
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));

        assertStatus(() -> turnoService.confirmar(5L), HttpStatus.BAD_REQUEST);
    }

    // --- marcarAusente ---

    @Test
    void marcarAusente_lo_rechaza_siempre_a_un_paciente() {
        Usuario usuarioPaciente = usuario(2L, Rol.PACIENTE);
        Paciente paciente = paciente(1L, usuarioPaciente);
        Turno turno = turno(5L, paciente, medico(1L, usuario(1L, Rol.MEDICO)), consultorio(1L),
                LocalDateTime.now().minusMinutes(5), EstadoTurno.CONFIRMADO);

        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente);
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));

        assertStatus(() -> turnoService.marcarAusente(5L), HttpStatus.FORBIDDEN);
    }

    @Test
    void marcarAusente_lo_permite_al_medico_del_turno_confirmado() {
        Usuario usuarioMedico = usuario(1L, Rol.MEDICO);
        Medico medico = medico(1L, usuarioMedico);
        Turno turno = turno(5L, paciente(1L, usuario(2L, Rol.PACIENTE)), medico, consultorio(1L),
                LocalDateTime.now().minusMinutes(5), EstadoTurno.CONFIRMADO);

        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioMedico);
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));
        when(turnoRepository.save(any(Turno.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(turnoService.marcarAusente(5L).estado()).isEqualTo(EstadoTurno.AUSENTE);
    }

    @Test
    void marcarAusente_rechaza_un_turno_que_no_esta_confirmado() {
        Usuario usuarioAdmin = usuario(1L, Rol.ADMIN);
        Turno turno = turno(5L, paciente(1L, usuario(2L, Rol.PACIENTE)), medico(1L, usuario(3L, Rol.MEDICO)), consultorio(1L),
                LocalDateTime.now().plusDays(1), EstadoTurno.RESERVADO);

        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioAdmin);
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));

        assertStatus(() -> turnoService.marcarAusente(5L), HttpStatus.BAD_REQUEST);
    }
}
