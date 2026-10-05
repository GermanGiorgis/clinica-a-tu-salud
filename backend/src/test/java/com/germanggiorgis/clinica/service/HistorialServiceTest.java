package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.ConsultaResponse;
import com.germanggiorgis.clinica.dto.HistorialEntryResponse;
import com.germanggiorgis.clinica.model.AccesoHistorial;
import com.germanggiorgis.clinica.model.Consulta;
import com.germanggiorgis.clinica.model.Consultorio;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static com.germanggiorgis.clinica.service.HttpStatusAssert.assertStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistorialServiceTest {

    @Mock
    private TurnoRepository turnoRepository;
    @Mock
    private ConsultaRepository consultaRepository;
    @Mock
    private PacienteRepository pacienteRepository;
    @Mock
    private MedicoRepository medicoRepository;
    @Mock
    private AccesoHistorialRepository accesoHistorialRepository;
    @Mock
    private UsuarioContextService usuarioContextService;
    @Mock
    private ConsultaService consultaService;
    @Mock
    private Clock clock;

    @InjectMocks
    private HistorialService historialService;

    @BeforeEach
    void relojFijo() {
        // 2026-10-05 12:00 en Buenos Aires
        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-10-05T15:00:00Z"));
        lenient().when(clock.getZone()).thenReturn(ZoneId.of("America/Argentina/Buenos_Aires"));
    }

    private Usuario usuario(long id, Rol rol) {
        return Usuario.builder().id(id).email("u" + id + "@clinica.test").rol(rol).nombre("N" + id).apellido("A").activo(true).build();
    }

    private Paciente paciente(long id, boolean verificado) {
        return Paciente.builder().id(id).usuario(usuario(100 + id, Rol.PACIENTE)).dni("D" + id)
                .fechaNacimiento(java.time.LocalDate.of(1990, 1, 1)).identidadVerificada(verificado).build();
    }

    private Turno turnoAtendido(Paciente paciente) {
        Medico medico = Medico.builder().id(1L).usuario(usuario(1L, Rol.MEDICO)).matricula("MP-1").especialidades(List.of()).build();
        return Turno.builder().id(5L).medico(medico).paciente(paciente)
                .consultorio(Consultorio.builder().id(1L).nombre("Consultorio A").build())
                .creadoPor(paciente.getUsuario()).inicio(LocalDateTime.now().minusDays(1))
                .fin(LocalDateTime.now().minusDays(1).plusMinutes(30)).estado(EstadoTurno.ATENDIDO).build();
    }

    @Test
    void mio_arma_el_historial_con_los_turnos_atendidos_del_paciente() {
        Usuario usuarioPaciente = usuario(2L, Rol.PACIENTE);
        Paciente paciente = Paciente.builder().id(1L).usuario(usuarioPaciente).dni("1").build();
        Turno turno = turnoAtendido(paciente);
        Consulta consulta = Consulta.builder().id(1L).turno(turno).build();
        ConsultaResponse consultaResponse = mock(ConsultaResponse.class);

        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente);
        when(pacienteRepository.findByUsuarioId(2L)).thenReturn(Optional.of(paciente));
        when(turnoRepository.findByPacienteIdAndEstadoOrderByInicioDesc(1L, EstadoTurno.ATENDIDO)).thenReturn(List.of(turno));
        when(consultaRepository.findByTurnoId(5L)).thenReturn(Optional.of(consulta));
        when(consultaService.armarCompleta(consulta)).thenReturn(consultaResponse);

        List<HistorialEntryResponse> historial = historialService.mio();

        assertThat(historial).hasSize(1);
        assertThat(historial.get(0).consultorioNombre()).isEqualTo("Consultorio A");
        assertThat(historial.get(0).consulta()).isSameAs(consultaResponse);
    }

    @Test
    void mio_no_se_anota_en_la_auditoria_porque_es_el_propio_paciente() {
        Usuario usuarioPaciente = usuario(2L, Rol.PACIENTE);
        Paciente paciente = Paciente.builder().id(1L).usuario(usuarioPaciente).dni("1").build();
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente);
        when(pacienteRepository.findByUsuarioId(2L)).thenReturn(Optional.of(paciente));
        when(turnoRepository.findByPacienteIdAndEstadoOrderByInicioDesc(1L, EstadoTurno.ATENDIDO)).thenReturn(List.of());

        historialService.mio();

        verifyNoInteractions(accesoHistorialRepository);
    }

    @Test
    void mio_rechaza_a_un_usuario_sin_perfil_de_paciente() {
        Usuario usuarioPaciente = usuario(2L, Rol.PACIENTE);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente);
        when(pacienteRepository.findByUsuarioId(2L)).thenReturn(Optional.empty());

        assertStatus(() -> historialService.mio(), HttpStatus.NOT_FOUND);
    }

    // --- historial de otra persona: médico ---

    @Test
    void dePaciente_lo_permite_a_un_medico_con_un_turno_que_no_creo_el_y_paciente_verificado() {
        Usuario usuarioMedico = usuario(11L, Rol.MEDICO);
        Medico medico = Medico.builder().id(1L).usuario(usuarioMedico).matricula("MP-1").build();
        Paciente paciente = paciente(3L, true);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioMedico);
        when(medicoRepository.findByUsuarioId(11L)).thenReturn(Optional.of(medico));
        when(turnoRepository.existsByMedicoIdAndPacienteIdAndEstadoInAndCreadoPorIdNot(eq(1L), eq(3L), any(), eq(11L))).thenReturn(true);
        when(pacienteRepository.findById(3L)).thenReturn(Optional.of(paciente));
        when(turnoRepository.findByPacienteIdAndEstadoOrderByInicioDesc(3L, EstadoTurno.ATENDIDO)).thenReturn(List.of());

        assertThat(historialService.dePaciente(3L)).isEmpty();
    }

    @Test
    void dePaciente_para_habilitar_exige_que_el_turno_no_lo_haya_creado_el_propio_medico_y_este_vigente_o_atendido() {
        // Lo que se pregunta a la base: ids distintos para médico (1) y para su usuario (11), para no confundirlos.
        Usuario usuarioMedico = usuario(11L, Rol.MEDICO);
        Medico medico = Medico.builder().id(1L).usuario(usuarioMedico).matricula("MP-1").build();
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioMedico);
        when(medicoRepository.findByUsuarioId(11L)).thenReturn(Optional.of(medico));
        when(turnoRepository.existsByMedicoIdAndPacienteIdAndEstadoInAndCreadoPorIdNot(any(), any(), any(), any())).thenReturn(false);

        assertStatus(() -> historialService.dePaciente(3L), HttpStatus.FORBIDDEN);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<EstadoTurno>> estados = ArgumentCaptor.forClass(Collection.class);
        verify(turnoRepository).existsByMedicoIdAndPacienteIdAndEstadoInAndCreadoPorIdNot(eq(1L), eq(3L), estados.capture(), eq(11L));
        assertThat(estados.getValue()).containsExactlyInAnyOrder(EstadoTurno.RESERVADO, EstadoTurno.CONFIRMADO, EstadoTurno.ATENDIDO);
    }

    @Test
    void dePaciente_rechaza_a_un_medico_sin_un_turno_que_lo_habilite_y_no_deja_rastro() {
        Usuario usuarioMedico = usuario(1L, Rol.MEDICO);
        Medico medico = Medico.builder().id(1L).usuario(usuarioMedico).matricula("MP-1").build();
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioMedico);
        when(medicoRepository.findByUsuarioId(1L)).thenReturn(Optional.of(medico));
        when(turnoRepository.existsByMedicoIdAndPacienteIdAndEstadoInAndCreadoPorIdNot(any(), any(), any(), any())).thenReturn(false);

        assertStatus(() -> historialService.dePaciente(3L), HttpStatus.FORBIDDEN);

        verifyNoInteractions(accesoHistorialRepository, pacienteRepository);
    }

    @Test
    void dePaciente_rechaza_a_un_medico_si_la_identidad_del_paciente_no_esta_verificada() {
        Usuario usuarioMedico = usuario(1L, Rol.MEDICO);
        Medico medico = Medico.builder().id(1L).usuario(usuarioMedico).matricula("MP-1").build();
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioMedico);
        when(medicoRepository.findByUsuarioId(1L)).thenReturn(Optional.of(medico));
        when(turnoRepository.existsByMedicoIdAndPacienteIdAndEstadoInAndCreadoPorIdNot(any(), any(), any(), any())).thenReturn(true);
        when(pacienteRepository.findById(3L)).thenReturn(Optional.of(paciente(3L, false)));

        assertStatus(() -> historialService.dePaciente(3L), HttpStatus.FORBIDDEN);

        verify(turnoRepository, never()).findByPacienteIdAndEstadoOrderByInicioDesc(any(), any());
        verifyNoInteractions(accesoHistorialRepository);
    }

    @Test
    void dePaciente_anota_el_acceso_de_un_medico_con_quien_cuando_y_a_quien() {
        Usuario usuarioMedico = usuario(11L, Rol.MEDICO);
        Medico medico = Medico.builder().id(1L).usuario(usuarioMedico).matricula("MP-1").build();
        Paciente paciente = paciente(3L, true);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioMedico);
        when(medicoRepository.findByUsuarioId(11L)).thenReturn(Optional.of(medico));
        when(turnoRepository.existsByMedicoIdAndPacienteIdAndEstadoInAndCreadoPorIdNot(any(), any(), any(), any())).thenReturn(true);
        when(pacienteRepository.findById(3L)).thenReturn(Optional.of(paciente));
        when(turnoRepository.findByPacienteIdAndEstadoOrderByInicioDesc(3L, EstadoTurno.ATENDIDO)).thenReturn(List.of());

        historialService.dePaciente(3L);

        ArgumentCaptor<AccesoHistorial> acceso = ArgumentCaptor.forClass(AccesoHistorial.class);
        verify(accesoHistorialRepository).save(acceso.capture());
        assertThat(acceso.getValue().getUsuario()).isSameAs(usuarioMedico);
        assertThat(acceso.getValue().getPaciente()).isSameAs(paciente);
        assertThat(acceso.getValue().getAccedidoEn()).isEqualTo(LocalDateTime.of(2026, 10, 5, 12, 0));
    }

    // --- historial de otra persona: admin y paciente ---

    @Test
    void dePaciente_lo_permite_a_un_admin_sin_chequear_la_relacion_y_lo_anota() {
        Usuario admin = usuario(9L, Rol.ADMIN);
        Paciente paciente = paciente(3L, false);
        when(usuarioContextService.getUsuarioActual()).thenReturn(admin);
        when(pacienteRepository.findById(3L)).thenReturn(Optional.of(paciente));
        when(turnoRepository.findByPacienteIdAndEstadoOrderByInicioDesc(3L, EstadoTurno.ATENDIDO)).thenReturn(List.of());

        assertThat(historialService.dePaciente(3L)).isEmpty();

        ArgumentCaptor<AccesoHistorial> acceso = ArgumentCaptor.forClass(AccesoHistorial.class);
        verify(accesoHistorialRepository).save(acceso.capture());
        assertThat(acceso.getValue().getUsuario()).isSameAs(admin);
        verify(turnoRepository, never()).existsByMedicoIdAndPacienteIdAndEstadoInAndCreadoPorIdNot(any(), any(), any(), any());
    }

    @Test
    void dePaciente_un_admin_que_pide_un_paciente_inexistente_recibe_404() {
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuario(9L, Rol.ADMIN));
        when(pacienteRepository.findById(3L)).thenReturn(Optional.empty());

        assertStatus(() -> historialService.dePaciente(3L), HttpStatus.NOT_FOUND);

        verifyNoInteractions(accesoHistorialRepository);
    }

    @Test
    void dePaciente_rechaza_a_un_paciente() {
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuario(2L, Rol.PACIENTE));

        assertStatus(() -> historialService.dePaciente(3L), HttpStatus.FORBIDDEN);

        verifyNoInteractions(accesoHistorialRepository);
    }
}
