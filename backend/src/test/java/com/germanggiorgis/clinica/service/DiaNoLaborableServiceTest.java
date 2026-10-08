package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.CancelarTurnoRequest;
import com.germanggiorgis.clinica.dto.CrearDiaNoLaborableRequest;
import com.germanggiorgis.clinica.dto.DiaNoLaborableResponse;
import com.germanggiorgis.clinica.dto.TurnoAfectadoResponse;
import com.germanggiorgis.clinica.model.Consultorio;
import com.germanggiorgis.clinica.model.DiaNoLaborable;
import com.germanggiorgis.clinica.model.EstadoTurno;
import com.germanggiorgis.clinica.model.Medico;
import com.germanggiorgis.clinica.model.MotivoCancelacion;
import com.germanggiorgis.clinica.model.Paciente;
import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Turno;
import com.germanggiorgis.clinica.model.Usuario;
import com.germanggiorgis.clinica.repository.DiaNoLaborableRepository;
import com.germanggiorgis.clinica.repository.MedicoRepository;
import com.germanggiorgis.clinica.repository.MotivoCancelacionRepository;
import com.germanggiorgis.clinica.repository.TurnoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static com.germanggiorgis.clinica.service.HttpStatusAssert.assertStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiaNoLaborableServiceTest {

    private static final ZoneId MENDOZA = ZoneId.of("America/Argentina/Mendoza");
    /** 2030-03-04 10:00 en Mendoza (UTC-3). */
    private static final LocalDateTime AHORA = LocalDateTime.of(2030, 3, 4, 10, 0);
    private static final LocalDate HOY = AHORA.toLocalDate();

    @Mock private DiaNoLaborableRepository diaNoLaborableRepository;
    @Mock private MedicoRepository medicoRepository;
    @Mock private TurnoRepository turnoRepository;
    @Mock private MotivoCancelacionRepository motivoCancelacionRepository;
    @Mock private UsuarioContextService usuarioContextService;
    @Spy private Clock clock = Clock.fixed(Instant.parse("2030-03-04T13:00:00Z"), MENDOZA);

    @InjectMocks
    private DiaNoLaborableService service;

    private Usuario recepcion;

    @BeforeEach
    void preparar() {
        recepcion = Usuario.builder().id(9L).email("r@clinica.test").passwordHash("x").rol(Rol.RECEPCIONISTA)
                .nombre("Rita").apellido("Mostrador").activo(true).build();
    }

    private Medico medico(long id) {
        Usuario usuario = Usuario.builder().id(id + 100).email("m" + id + "@clinica.test").passwordHash("x").rol(Rol.MEDICO)
                .nombre("Mario").apellido("Medico" + id).activo(true).build();
        return Medico.builder().id(id).usuario(usuario).matricula("MP" + id).especialidades(List.of()).build();
    }

    private Turno turno(long id, Medico medico, LocalDateTime inicio) {
        Usuario pacienteUsuario = Usuario.builder().id(id + 200).email("p" + id + "@clinica.test").passwordHash("x").rol(Rol.PACIENTE)
                .nombre("Pablo").apellido("Paciente").telefono("261-555-000" + id).activo(true).build();
        Paciente paciente = Paciente.builder().id(id).usuario(pacienteUsuario).dni("DNI" + id).fechaNacimiento(LocalDate.of(1990, 1, 1)).build();
        return Turno.builder().id(id).medico(medico).paciente(paciente).consultorio(Consultorio.builder().id(1L).nombre("Consultorio 1").build())
                .creadoPor(pacienteUsuario).inicio(inicio).fin(inicio.plusMinutes(30)).estado(EstadoTurno.RESERVADO).build();
    }

    private DiaNoLaborable cierre(LocalDate desde, LocalDate hasta, Medico medico) {
        return DiaNoLaborable.builder().id(1L).desde(desde).hasta(hasta).descripcion("Feriado").medico(medico).build();
    }

    // --- crear ---

    @Test
    void crear_guarda_un_feriado_de_toda_la_clinica_con_quien_lo_cargo_y_cuando() {
        when(usuarioContextService.getUsuarioActual()).thenReturn(recepcion);
        when(diaNoLaborableRepository.save(any(DiaNoLaborable.class))).thenAnswer(inv -> inv.getArgument(0));

        DiaNoLaborableResponse respuesta = service.crear(
                new CrearDiaNoLaborableRequest(HOY.plusDays(10), HOY.plusDays(11), "  Día de la Vendimia  ", null));

        ArgumentCaptor<DiaNoLaborable> guardado = ArgumentCaptor.forClass(DiaNoLaborable.class);
        verify(diaNoLaborableRepository).save(guardado.capture());
        assertThat(guardado.getValue().getCreadoPor()).isSameAs(recepcion);
        assertThat(guardado.getValue().getCreadoEn()).isEqualTo(AHORA);
        assertThat(guardado.getValue().getMedico()).isNull();
        assertThat(guardado.getValue().getDescripcion()).isEqualTo("Día de la Vendimia");
        assertThat(respuesta.medicoId()).isNull();
    }

    @Test
    void crear_una_ausencia_de_medico_la_asocia_a_ese_medico() {
        Medico medico = medico(3L);
        when(medicoRepository.findById(3L)).thenReturn(Optional.of(medico));
        when(usuarioContextService.getUsuarioActual()).thenReturn(recepcion);
        when(diaNoLaborableRepository.save(any(DiaNoLaborable.class))).thenAnswer(inv -> inv.getArgument(0));

        DiaNoLaborableResponse respuesta = service.crear(new CrearDiaNoLaborableRequest(HOY, HOY.plusDays(2), "Licencia", 3L));

        assertThat(respuesta.medicoId()).isEqualTo(3L);
        assertThat(respuesta.medicoNombre()).isEqualTo("Mario Medico3");
    }

    @Test
    void crear_acepta_un_periodo_que_empieza_antes_de_hoy_si_todavia_no_termino() {
        when(usuarioContextService.getUsuarioActual()).thenReturn(recepcion);
        when(diaNoLaborableRepository.save(any(DiaNoLaborable.class))).thenAnswer(inv -> inv.getArgument(0));

        // un medico que se enfermo ayer y vuelve en 3 dias: se carga hoy, desde ayer
        assertThat(service.crear(new CrearDiaNoLaborableRequest(HOY.minusDays(1), HOY.plusDays(3), "Reposo", null))).isNotNull();
        // y uno que termina hoy mismo todavia vale
        assertThat(service.crear(new CrearDiaNoLaborableRequest(HOY, HOY, "Hoy", null))).isNotNull();
    }

    @Test
    void crear_rechaza_la_fecha_final_anterior_a_la_inicial() {
        assertStatus(() -> service.crear(new CrearDiaNoLaborableRequest(HOY.plusDays(5), HOY.plusDays(4), "x", null)), HttpStatus.BAD_REQUEST);
        verify(diaNoLaborableRepository, never()).save(any());
    }

    @Test
    void crear_rechaza_un_periodo_que_ya_termino() {
        assertStatus(() -> service.crear(new CrearDiaNoLaborableRequest(HOY.minusDays(5), HOY.minusDays(1), "x", null)), HttpStatus.BAD_REQUEST);
    }

    @Test
    void crear_rechaza_mas_de_365_dias_pero_acepta_exactamente_365() {
        assertStatus(() -> service.crear(new CrearDiaNoLaborableRequest(HOY, HOY.plusDays(366), "x", null)), HttpStatus.BAD_REQUEST);

        when(usuarioContextService.getUsuarioActual()).thenReturn(recepcion);
        when(diaNoLaborableRepository.save(any(DiaNoLaborable.class))).thenAnswer(inv -> inv.getArgument(0));
        assertThat(service.crear(new CrearDiaNoLaborableRequest(HOY, HOY.plusDays(365), "Cierre anual", null))).isNotNull();
    }

    @Test
    void crear_rechaza_un_medico_inexistente() {
        when(medicoRepository.findById(77L)).thenReturn(Optional.empty());

        assertStatus(() -> service.crear(new CrearDiaNoLaborableRequest(HOY, HOY, "x", 77L)), HttpStatus.NOT_FOUND);
        verify(diaNoLaborableRepository, never()).save(any());
    }

    // --- listar / eliminar ---

    @Test
    void listar_pide_solo_los_que_no_terminaron_con_el_dia_de_hoy_de_la_clinica() {
        when(diaNoLaborableRepository.findByHastaGreaterThanEqualOrderByDesdeAscIdAsc(HOY))
                .thenReturn(List.of(cierre(HOY, HOY.plusDays(1), null)));

        assertThat(service.listar()).hasSize(1);
    }

    @Test
    void eliminar_borra_el_periodo_y_rechaza_uno_inexistente() {
        DiaNoLaborable dia = cierre(HOY, HOY, null);
        when(diaNoLaborableRepository.findById(1L)).thenReturn(Optional.of(dia));
        when(diaNoLaborableRepository.findById(2L)).thenReturn(Optional.empty());

        service.eliminar(1L);

        verify(diaNoLaborableRepository).delete(dia);
        assertStatus(() -> service.eliminar(2L), HttpStatus.NOT_FOUND);
    }

    // --- turnos afectados ---

    @Test
    void turnosAfectados_de_un_feriado_de_la_clinica_busca_en_todas_las_agendas_y_trae_el_telefono() {
        DiaNoLaborable dia = cierre(HOY.plusDays(3), HOY.plusDays(4), null);
        Turno turno = turno(1L, medico(3L), HOY.plusDays(3).atTime(9, 0));
        when(diaNoLaborableRepository.findById(1L)).thenReturn(Optional.of(dia));
        when(turnoRepository.findByEstadoInAndInicioGreaterThanEqualAndInicioLessThanOrderByInicioAsc(
                anyCollection(), eq(HOY.plusDays(3).atStartOfDay()), eq(HOY.plusDays(5).atStartOfDay())))
                .thenReturn(List.of(turno));

        List<TurnoAfectadoResponse> afectados = service.turnosAfectados(1L);

        assertThat(afectados).hasSize(1);
        assertThat(afectados.get(0).pacienteTelefono()).isEqualTo("261-555-0001");
        assertThat(afectados.get(0).medicoNombre()).isEqualTo("Mario Medico3");
    }

    @Test
    void turnosAfectados_de_una_ausencia_busca_solo_en_la_agenda_de_ese_medico() {
        Medico medico = medico(3L);
        DiaNoLaborable dia = cierre(HOY.plusDays(1), HOY.plusDays(1), medico);
        when(diaNoLaborableRepository.findById(1L)).thenReturn(Optional.of(dia));
        when(turnoRepository.findByMedicoIdAndEstadoInAndInicioGreaterThanEqualAndInicioLessThanOrderByInicioAsc(
                eq(3L), anyCollection(), any(), any())).thenReturn(List.of());

        assertThat(service.turnosAfectados(1L)).isEmpty();
        verify(turnoRepository, never()).findByEstadoInAndInicioGreaterThanEqualAndInicioLessThanOrderByInicioAsc(any(), any(), any());
    }

    @Test
    void turnosAfectados_no_incluye_los_turnos_que_ya_empezaron_cuando_el_periodo_arranco_antes_de_ahora() {
        DiaNoLaborable dia = cierre(HOY.minusDays(1), HOY.plusDays(1), null);
        when(diaNoLaborableRepository.findById(1L)).thenReturn(Optional.of(dia));
        when(turnoRepository.findByEstadoInAndInicioGreaterThanEqualAndInicioLessThanOrderByInicioAsc(
                anyCollection(), eq(AHORA), eq(HOY.plusDays(2).atStartOfDay()))).thenReturn(List.of());

        assertThat(service.turnosAfectados(1L)).isEmpty();
    }

    @Test
    void turnosAfectados_considera_solo_los_turnos_pendientes() {
        DiaNoLaborable dia = cierre(HOY.plusDays(1), HOY.plusDays(1), null);
        when(diaNoLaborableRepository.findById(1L)).thenReturn(Optional.of(dia));
        when(turnoRepository.findByEstadoInAndInicioGreaterThanEqualAndInicioLessThanOrderByInicioAsc(any(), any(), any()))
                .thenReturn(List.of());

        service.turnosAfectados(1L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<EstadoTurno>> estados = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(turnoRepository).findByEstadoInAndInicioGreaterThanEqualAndInicioLessThanOrderByInicioAsc(estados.capture(), any(), any());
        assertThat(estados.getValue()).containsExactlyInAnyOrder(EstadoTurno.RESERVADO, EstadoTurno.CONFIRMADO);
    }

    // --- cancelar en bloque ---

    @Test
    void cancelarTurnosAfectados_cancela_todos_con_el_mismo_motivo_y_devuelve_cuantos() {
        DiaNoLaborable dia = cierre(HOY.plusDays(1), HOY.plusDays(1), null);
        Medico medico = medico(3L);
        Turno a = turno(1L, medico, HOY.plusDays(1).atTime(9, 0));
        Turno b = turno(2L, medico, HOY.plusDays(1).atTime(9, 30));
        b.setEstado(EstadoTurno.CONFIRMADO);
        MotivoCancelacion motivo = MotivoCancelacion.builder().id(5L).nombre("Médico no disponible").build();
        when(diaNoLaborableRepository.findById(1L)).thenReturn(Optional.of(dia));
        when(motivoCancelacionRepository.findById(5L)).thenReturn(Optional.of(motivo));
        when(turnoRepository.findByEstadoInAndInicioGreaterThanEqualAndInicioLessThanOrderByInicioAsc(any(), any(), any()))
                .thenReturn(List.of(a, b));

        int cancelados = service.cancelarTurnosAfectados(1L, new CancelarTurnoRequest(5L));

        assertThat(cancelados).isEqualTo(2);
        assertThat(List.of(a, b)).allSatisfy(t -> {
            assertThat(t.getEstado()).isEqualTo(EstadoTurno.CANCELADO);
            assertThat(t.getMotivoCancelacion()).isSameAs(motivo);
        });
        verify(turnoRepository).saveAll(List.of(a, b));
    }

    @Test
    void cancelarTurnosAfectados_rechaza_un_motivo_inexistente_sin_tocar_ningun_turno() {
        DiaNoLaborable dia = cierre(HOY.plusDays(1), HOY.plusDays(1), null);
        when(diaNoLaborableRepository.findById(1L)).thenReturn(Optional.of(dia));
        when(motivoCancelacionRepository.findById(99L)).thenReturn(Optional.empty());

        assertStatus(() -> service.cancelarTurnosAfectados(1L, new CancelarTurnoRequest(99L)), HttpStatus.NOT_FOUND);
        verify(turnoRepository, never()).saveAll(any());
    }

    @Test
    void cancelarTurnosAfectados_rechaza_un_periodo_inexistente() {
        when(diaNoLaborableRepository.findById(404L)).thenReturn(Optional.empty());

        assertStatus(() -> service.cancelarTurnosAfectados(404L, new CancelarTurnoRequest(1L)), HttpStatus.NOT_FOUND);
    }
}
