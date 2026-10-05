package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.HorarioAtencionRequest;
import com.germanggiorgis.clinica.dto.HorarioAtencionResponse;
import com.germanggiorgis.clinica.model.Consultorio;
import com.germanggiorgis.clinica.model.EstadoTurno;
import com.germanggiorgis.clinica.model.HorarioAtencion;
import com.germanggiorgis.clinica.model.Medico;
import com.germanggiorgis.clinica.model.Turno;
import com.germanggiorgis.clinica.model.Usuario;
import com.germanggiorgis.clinica.repository.ConsultorioRepository;
import com.germanggiorgis.clinica.repository.HorarioAtencionRepository;
import com.germanggiorgis.clinica.repository.MedicoRepository;
import com.germanggiorgis.clinica.repository.TurnoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
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
class HorarioAtencionServiceTest {

    @Mock
    private HorarioAtencionRepository horarioAtencionRepository;
    @Mock
    private MedicoRepository medicoRepository;
    @Mock
    private ConsultorioRepository consultorioRepository;
    @Mock
    private TurnoRepository turnoRepository;

    @InjectMocks
    private HorarioAtencionService horarioAtencionService;

    private Medico medico(long id) {
        Usuario usuario = Usuario.builder().id(id).email("m" + id + "@clinica.test").rol(com.germanggiorgis.clinica.model.Rol.MEDICO)
                .nombre("N").apellido("A").activo(true).build();
        return Medico.builder().id(id).usuario(usuario).matricula("MP-" + id).especialidades(List.of()).build();
    }

    private Consultorio consultorio(long id) {
        return Consultorio.builder().id(id).nombre("C" + id).build();
    }

    private HorarioAtencionRequest request(long medicoId, long consultorioId, LocalTime inicio, LocalTime fin) {
        return new HorarioAtencionRequest(medicoId, consultorioId, DayOfWeek.MONDAY, inicio, fin, 30,
                LocalDate.of(2026, 1, 1), null);
    }

    @Test
    void crear_horario_valido() {
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico(1L)));
        when(consultorioRepository.findById(1L)).thenReturn(Optional.of(consultorio(1L)));
        when(horarioAtencionRepository.findByMedicoIdAndDiaSemana(1L, DayOfWeek.MONDAY)).thenReturn(List.of());
        when(horarioAtencionRepository.findByConsultorioIdAndDiaSemana(1L, DayOfWeek.MONDAY)).thenReturn(List.of());
        when(horarioAtencionRepository.save(any(HorarioAtencion.class))).thenAnswer(inv -> {
            HorarioAtencion h = inv.getArgument(0);
            h.setId(1L);
            return h;
        });

        HorarioAtencionResponse respuesta = horarioAtencionService.crear(request(1L, 1L, LocalTime.of(9, 0), LocalTime.of(12, 0)));

        assertThat(respuesta.id()).isEqualTo(1L);
    }

    @Test
    void crear_rechaza_hora_fin_anterior_a_inicio() {
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico(1L)));
        when(consultorioRepository.findById(1L)).thenReturn(Optional.of(consultorio(1L)));

        assertStatus(() -> horarioAtencionService.crear(request(1L, 1L, LocalTime.of(12, 0), LocalTime.of(9, 0))),
                HttpStatus.BAD_REQUEST);
    }

    @Test
    void crear_rechaza_superposicion_del_mismo_medico() {
        HorarioAtencion existente = HorarioAtencion.builder().id(5L).medico(medico(1L)).consultorio(consultorio(2L))
                .diaSemana(DayOfWeek.MONDAY).horaInicio(LocalTime.of(10, 0)).horaFin(LocalTime.of(13, 0))
                .duracionTurnoMin(30).vigenteDesde(LocalDate.of(2026, 1, 1)).build();

        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico(1L)));
        when(consultorioRepository.findById(1L)).thenReturn(Optional.of(consultorio(1L)));
        when(horarioAtencionRepository.findByMedicoIdAndDiaSemana(1L, DayOfWeek.MONDAY)).thenReturn(List.of(existente));

        assertStatus(() -> horarioAtencionService.crear(request(1L, 1L, LocalTime.of(9, 0), LocalTime.of(11, 0))),
                HttpStatus.CONFLICT);
    }

    @Test
    void crear_rechaza_consultorio_ocupado_por_otro_medico() {
        HorarioAtencion existente = HorarioAtencion.builder().id(5L).medico(medico(2L)).consultorio(consultorio(1L))
                .diaSemana(DayOfWeek.MONDAY).horaInicio(LocalTime.of(10, 0)).horaFin(LocalTime.of(13, 0))
                .duracionTurnoMin(30).vigenteDesde(LocalDate.of(2026, 1, 1)).build();

        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico(1L)));
        when(consultorioRepository.findById(1L)).thenReturn(Optional.of(consultorio(1L)));
        when(horarioAtencionRepository.findByMedicoIdAndDiaSemana(1L, DayOfWeek.MONDAY)).thenReturn(List.of());
        when(horarioAtencionRepository.findByConsultorioIdAndDiaSemana(1L, DayOfWeek.MONDAY)).thenReturn(List.of(existente));

        assertStatus(() -> horarioAtencionService.crear(request(1L, 1L, LocalTime.of(9, 0), LocalTime.of(11, 0))),
                HttpStatus.CONFLICT);
    }

    @Test
    void crear_no_superponerse_consigo_mismo_no_es_conflicto() {
        // Dos franjas que no se tocan (9-11 y 11-13) en el mismo dia no deben chocar.
        HorarioAtencion existente = HorarioAtencion.builder().id(5L).medico(medico(1L)).consultorio(consultorio(1L))
                .diaSemana(DayOfWeek.MONDAY).horaInicio(LocalTime.of(11, 0)).horaFin(LocalTime.of(13, 0))
                .duracionTurnoMin(30).vigenteDesde(LocalDate.of(2026, 1, 1)).build();

        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico(1L)));
        when(consultorioRepository.findById(1L)).thenReturn(Optional.of(consultorio(1L)));
        when(horarioAtencionRepository.findByMedicoIdAndDiaSemana(1L, DayOfWeek.MONDAY)).thenReturn(List.of(existente));
        when(horarioAtencionRepository.findByConsultorioIdAndDiaSemana(1L, DayOfWeek.MONDAY)).thenReturn(List.of(existente));
        when(horarioAtencionRepository.save(any(HorarioAtencion.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(horarioAtencionService.crear(request(1L, 1L, LocalTime.of(9, 0), LocalTime.of(11, 0)))).isNotNull();
    }

    @Test
    void actualizar_se_ignora_a_si_mismo_al_chequear_superposicion() {
        HorarioAtencion propio = HorarioAtencion.builder().id(5L).medico(medico(1L)).consultorio(consultorio(1L))
                .diaSemana(DayOfWeek.MONDAY).horaInicio(LocalTime.of(9, 0)).horaFin(LocalTime.of(11, 0))
                .duracionTurnoMin(30).vigenteDesde(LocalDate.of(2026, 1, 1)).build();

        when(horarioAtencionRepository.findById(5L)).thenReturn(Optional.of(propio));
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico(1L)));
        when(consultorioRepository.findById(1L)).thenReturn(Optional.of(consultorio(1L)));
        when(horarioAtencionRepository.findByMedicoIdAndDiaSemana(1L, DayOfWeek.MONDAY)).thenReturn(List.of(propio));
        when(horarioAtencionRepository.findByConsultorioIdAndDiaSemana(1L, DayOfWeek.MONDAY)).thenReturn(List.of(propio));
        when(horarioAtencionRepository.save(any(HorarioAtencion.class))).thenAnswer(inv -> inv.getArgument(0));

        turnosActivosDelMedico();

        // Mismo horario (9-11), no debería chocar contra si mismo.
        HorarioAtencionResponse respuesta = horarioAtencionService.actualizar(5L, request(1L, 1L, LocalTime.of(9, 0), LocalTime.of(11, 0)));

        assertThat(respuesta).isNotNull();
    }

    @Test
    void eliminar_horario_inexistente_es_404() {
        when(horarioAtencionRepository.findById(1L)).thenReturn(Optional.empty());
        assertStatus(() -> horarioAtencionService.eliminar(1L), HttpStatus.NOT_FOUND);
    }

    @Test
    void listar_filtra_por_medico_cuando_se_indica() {
        when(horarioAtencionRepository.findByMedicoId(1L)).thenReturn(List.of());
        assertThat(horarioAtencionService.listar(1L)).isEmpty();
    }

    @Test
    void listar_sin_filtro_devuelve_todos() {
        when(horarioAtencionRepository.findAll()).thenReturn(List.of());
        assertThat(horarioAtencionService.listar(null)).isEmpty();
    }

    // --- un horario no se puede cambiar ni borrar si hay turnos activos que dependen de él ---

    private HorarioAtencion lunes9a12() {
        return HorarioAtencion.builder().id(5L).medico(medico(1L)).consultorio(consultorio(1L))
                .diaSemana(DayOfWeek.MONDAY).horaInicio(LocalTime.of(9, 0)).horaFin(LocalTime.of(12, 0))
                .duracionTurnoMin(30).vigenteDesde(LocalDate.of(2026, 1, 1)).build();
    }

    private LocalDate proximoLunes() {
        return LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    private Turno turnoActivo(LocalDate fecha, LocalTime hora, long consultorioId) {
        Usuario paciente = Usuario.builder().id(9L).email("p@clinica.test").rol(com.germanggiorgis.clinica.model.Rol.PACIENTE)
                .nombre("P").apellido("P").activo(true).build();
        LocalDateTime inicio = LocalDateTime.of(fecha, hora);
        return Turno.builder().id(70L).medico(medico(1L)).consultorio(consultorio(consultorioId)).creadoPor(paciente)
                .inicio(inicio).fin(inicio.plusMinutes(30)).estado(EstadoTurno.RESERVADO).build();
    }

    private void turnosActivosDelMedico(Turno... turnos) {
        when(turnoRepository.findByMedicoIdAndEstadoInAndInicioGreaterThanEqual(eq(1L), anyCollection(), any(LocalDateTime.class)))
                .thenReturn(List.of(turnos));
    }

    private HorarioAtencionRequest cambio(DayOfWeek dia, long consultorioId, LocalTime inicio, LocalTime fin, int duracion) {
        return new HorarioAtencionRequest(1L, consultorioId, dia, inicio, fin, duracion, LocalDate.of(2026, 1, 1), null);
    }

    private void prepararActualizacion(HorarioAtencion horario) {
        prepararActualizacion(horario, 1L);
    }

    /** consultorioDelCambio: el consultorio que va a pedir la actualización. */
    private void prepararActualizacion(HorarioAtencion horario, long consultorioDelCambio) {
        when(horarioAtencionRepository.findById(5L)).thenReturn(Optional.of(horario));
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico(1L)));
        when(consultorioRepository.findById(consultorioDelCambio)).thenReturn(Optional.of(consultorio(consultorioDelCambio)));
        when(horarioAtencionRepository.findByMedicoIdAndDiaSemana(any(), any())).thenReturn(List.of(horario));
        when(horarioAtencionRepository.findByConsultorioIdAndDiaSemana(any(), any())).thenReturn(List.of(horario));
    }

    @Test
    void eliminar_con_turnos_activos_futuros_es_409_y_no_borra() {
        HorarioAtencion horario = lunes9a12();
        when(horarioAtencionRepository.findById(5L)).thenReturn(Optional.of(horario));
        turnosActivosDelMedico(turnoActivo(proximoLunes(), LocalTime.of(10, 0), 1L));

        assertStatus(() -> horarioAtencionService.eliminar(5L), HttpStatus.CONFLICT);

        verify(horarioAtencionRepository, never()).delete(any(HorarioAtencion.class));
    }

    @Test
    void eliminar_ignora_turnos_de_otro_dia_o_de_otro_consultorio_o_fuera_de_franja() {
        HorarioAtencion horario = lunes9a12();
        when(horarioAtencionRepository.findById(5L)).thenReturn(Optional.of(horario));
        turnosActivosDelMedico(
                turnoActivo(proximoLunes().plusDays(1), LocalTime.of(10, 0), 1L),   // martes
                turnoActivo(proximoLunes(), LocalTime.of(10, 0), 2L),               // otro consultorio
                turnoActivo(proximoLunes(), LocalTime.of(15, 0), 1L));              // fuera de 9 a 12

        horarioAtencionService.eliminar(5L);

        verify(horarioAtencionRepository).delete(horario);
    }

    @Test
    void eliminar_sin_turnos_activos_borra() {
        HorarioAtencion horario = lunes9a12();
        when(horarioAtencionRepository.findById(5L)).thenReturn(Optional.of(horario));
        turnosActivosDelMedico();

        horarioAtencionService.eliminar(5L);

        verify(horarioAtencionRepository).delete(horario);
    }

    @Test
    void cambiar_la_duracion_con_turnos_activos_es_409() {
        HorarioAtencion horario = lunes9a12();
        prepararActualizacion(horario);
        turnosActivosDelMedico(turnoActivo(proximoLunes(), LocalTime.of(9, 30), 1L));

        assertStatus(() -> horarioAtencionService.actualizar(5L, cambio(DayOfWeek.MONDAY, 1L, LocalTime.of(9, 0), LocalTime.of(12, 0), 20)),
                HttpStatus.CONFLICT);

        verify(horarioAtencionRepository, never()).save(any(HorarioAtencion.class));
    }

    @Test
    void cambiar_el_dia_con_turnos_activos_es_409() {
        HorarioAtencion horario = lunes9a12();
        prepararActualizacion(horario);
        turnosActivosDelMedico(turnoActivo(proximoLunes(), LocalTime.of(10, 0), 1L));

        assertStatus(() -> horarioAtencionService.actualizar(5L, cambio(DayOfWeek.TUESDAY, 1L, LocalTime.of(9, 0), LocalTime.of(12, 0), 30)),
                HttpStatus.CONFLICT);
    }

    @Test
    void cambiar_de_consultorio_con_turnos_activos_es_409() {
        HorarioAtencion horario = lunes9a12();
        prepararActualizacion(horario, 2L);
        turnosActivosDelMedico(turnoActivo(proximoLunes(), LocalTime.of(10, 0), 1L));

        assertStatus(() -> horarioAtencionService.actualizar(5L, cambio(DayOfWeek.MONDAY, 2L, LocalTime.of(9, 0), LocalTime.of(12, 0), 30)),
                HttpStatus.CONFLICT);
    }

    @Test
    void acortar_el_horario_y_dejar_un_turno_afuera_es_409() {
        HorarioAtencion horario = lunes9a12();
        prepararActualizacion(horario);
        turnosActivosDelMedico(turnoActivo(proximoLunes(), LocalTime.of(11, 0), 1L));

        assertStatus(() -> horarioAtencionService.actualizar(5L, cambio(DayOfWeek.MONDAY, 1L, LocalTime.of(9, 0), LocalTime.of(10, 30), 30)),
                HttpStatus.CONFLICT);
    }

    @Test
    void ampliar_el_horario_manteniendo_la_grilla_se_permite_aunque_haya_turnos() {
        HorarioAtencion horario = lunes9a12();
        prepararActualizacion(horario);
        when(horarioAtencionRepository.save(any(HorarioAtencion.class))).thenAnswer(inv -> inv.getArgument(0));
        turnosActivosDelMedico(turnoActivo(proximoLunes(), LocalTime.of(10, 0), 1L));

        HorarioAtencionResponse r = horarioAtencionService.actualizar(5L, cambio(DayOfWeek.MONDAY, 1L, LocalTime.of(9, 0), LocalTime.of(14, 0), 30));

        assertThat(r.horaFin()).isEqualTo(LocalTime.of(14, 0));
    }

    @Test
    void sin_turnos_que_dependan_de_el_se_puede_cambiar_la_duracion() {
        HorarioAtencion horario = lunes9a12();
        prepararActualizacion(horario);
        when(horarioAtencionRepository.save(any(HorarioAtencion.class))).thenAnswer(inv -> inv.getArgument(0));
        turnosActivosDelMedico();

        HorarioAtencionResponse r = horarioAtencionService.actualizar(5L, cambio(DayOfWeek.MONDAY, 1L, LocalTime.of(9, 0), LocalTime.of(12, 0), 20));

        assertThat(r.duracionTurnoMin()).isEqualTo(20);
    }
}
