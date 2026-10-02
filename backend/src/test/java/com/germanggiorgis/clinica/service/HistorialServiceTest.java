package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.ConsultaResponse;
import com.germanggiorgis.clinica.dto.HistorialEntryResponse;
import com.germanggiorgis.clinica.model.Consulta;
import com.germanggiorgis.clinica.model.Consultorio;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.germanggiorgis.clinica.service.HttpStatusAssert.assertStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
    private UsuarioContextService usuarioContextService;
    @Mock
    private ConsultaService consultaService;

    @InjectMocks
    private HistorialService historialService;

    private Usuario usuario(long id, Rol rol) {
        return Usuario.builder().id(id).email("u" + id + "@clinica.test").rol(rol).nombre("N" + id).apellido("A").activo(true).build();
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
    void mio_rechaza_a_un_usuario_sin_perfil_de_paciente() {
        Usuario usuarioPaciente = usuario(2L, Rol.PACIENTE);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente);
        when(pacienteRepository.findByUsuarioId(2L)).thenReturn(Optional.empty());

        assertStatus(() -> historialService.mio(), HttpStatus.NOT_FOUND);
    }

    @Test
    void dePaciente_lo_permite_a_un_medico_que_tuvo_turnos_con_el_paciente() {
        Usuario usuarioMedico = usuario(1L, Rol.MEDICO);
        Medico medico = Medico.builder().id(1L).usuario(usuarioMedico).matricula("MP-1").build();
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioMedico);
        when(medicoRepository.findByUsuarioId(1L)).thenReturn(Optional.of(medico));
        when(turnoRepository.existsByMedicoIdAndPacienteId(1L, 3L)).thenReturn(true);
        when(turnoRepository.findByPacienteIdAndEstadoOrderByInicioDesc(3L, EstadoTurno.ATENDIDO)).thenReturn(List.of());

        assertThat(historialService.dePaciente(3L)).isEmpty();
    }

    @Test
    void dePaciente_rechaza_a_un_medico_que_nunca_atendio_a_ese_paciente() {
        Usuario usuarioMedico = usuario(1L, Rol.MEDICO);
        Medico medico = Medico.builder().id(1L).usuario(usuarioMedico).matricula("MP-1").build();
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioMedico);
        when(medicoRepository.findByUsuarioId(1L)).thenReturn(Optional.of(medico));
        when(turnoRepository.existsByMedicoIdAndPacienteId(1L, 3L)).thenReturn(false);

        assertStatus(() -> historialService.dePaciente(3L), HttpStatus.FORBIDDEN);
    }

    @Test
    void dePaciente_lo_permite_a_un_admin_sin_chequear_la_relacion() {
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuario(9L, Rol.ADMIN));
        when(turnoRepository.findByPacienteIdAndEstadoOrderByInicioDesc(3L, EstadoTurno.ATENDIDO)).thenReturn(List.of());

        assertThat(historialService.dePaciente(3L)).isEmpty();
    }

    @Test
    void dePaciente_rechaza_a_un_paciente() {
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuario(2L, Rol.PACIENTE));
        assertStatus(() -> historialService.dePaciente(3L), HttpStatus.FORBIDDEN);
    }
}
