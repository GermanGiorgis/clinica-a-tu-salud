package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.ConsultaResponse;
import com.germanggiorgis.clinica.dto.CrearConsultaRequest;
import com.germanggiorgis.clinica.dto.DiagnosticoRequest;
import com.germanggiorgis.clinica.model.Consulta;
import com.germanggiorgis.clinica.model.Consultorio;
import com.germanggiorgis.clinica.model.EstadoTurno;
import com.germanggiorgis.clinica.model.Medico;
import com.germanggiorgis.clinica.model.Paciente;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.germanggiorgis.clinica.service.HttpStatusAssert.assertStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultaServiceTest {

    @Mock
    private ConsultaRepository consultaRepository;
    @Mock
    private DiagnosticoRepository diagnosticoRepository;
    @Mock
    private RecetaRepository recetaRepository;
    @Mock
    private ItemRecetaRepository itemRecetaRepository;
    @Mock
    private OrdenEstudioRepository ordenEstudioRepository;
    @Mock
    private ResultadoEstudioRepository resultadoEstudioRepository;
    @Mock
    private TurnoRepository turnoRepository;
    @Mock
    private UsuarioContextService usuarioContextService;
    @Spy
    private Clock clock = Clock.systemDefaultZone();

    @InjectMocks
    private ConsultaService consultaService;

    private Usuario usuario(long id, Rol rol) {
        return Usuario.builder().id(id).email("u" + id + "@clinica.test").rol(rol).nombre("N").apellido("A").activo(true).build();
    }

    private Turno turno(long id, Usuario medicoUsuario, Usuario pacienteUsuario, EstadoTurno estado) {
        Medico medico = Medico.builder().id(1L).usuario(medicoUsuario).matricula("MP-1").especialidades(List.of()).build();
        Paciente paciente = Paciente.builder().id(1L).usuario(pacienteUsuario).dni("1").build();
        return Turno.builder().id(id).medico(medico).paciente(paciente).consultorio(Consultorio.builder().id(1L).nombre("C1").build())
                .creadoPor(pacienteUsuario).inicio(LocalDateTime.now()).fin(LocalDateTime.now().plusMinutes(30)).estado(estado).build();
    }

    @Test
    void crear_pasa_el_turno_a_atendido() {
        Usuario usuarioMedico = usuario(1L, Rol.MEDICO);
        Turno turno = turno(5L, usuarioMedico, usuario(2L, Rol.PACIENTE), EstadoTurno.CONFIRMADO);

        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioMedico);
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));
        when(consultaRepository.findByTurnoId(5L)).thenReturn(Optional.empty());
        when(consultaRepository.save(any(Consulta.class))).thenAnswer(inv -> {
            Consulta c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });
        when(diagnosticoRepository.findByConsultaId(1L)).thenReturn(List.of());
        when(recetaRepository.findByConsultaId(1L)).thenReturn(List.of());
        when(ordenEstudioRepository.findByConsultaId(1L)).thenReturn(List.of());

        CrearConsultaRequest request = new CrearConsultaRequest("Notas", List.of(new DiagnosticoRequest("Gripe", null)));
        ConsultaResponse respuesta = consultaService.crear(5L, request);

        assertThat(respuesta.id()).isEqualTo(1L);
        assertThat(turno.getEstado()).isEqualTo(EstadoTurno.ATENDIDO);
    }

    @Test
    void crear_rechaza_atender_un_turno_que_es_otro_dia() {
        Usuario usuarioMedico = usuario(1L, Rol.MEDICO);
        Turno turno = turno(5L, usuarioMedico, usuario(2L, Rol.PACIENTE), EstadoTurno.CONFIRMADO);
        turno.setInicio(LocalDateTime.now().plusDays(1));
        turno.setFin(turno.getInicio().plusMinutes(30));

        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioMedico);
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));

        CrearConsultaRequest request = new CrearConsultaRequest("Notas", List.of(new DiagnosticoRequest("Gripe", null)));
        assertStatus(() -> consultaService.crear(5L, request), HttpStatus.BAD_REQUEST);
        assertThat(turno.getEstado()).isEqualTo(EstadoTurno.CONFIRMADO);
        verify(consultaRepository, never()).save(any());
    }

    @Test
    void crear_deja_atender_un_turno_de_hoy_aunque_todavia_no_sea_la_hora_y_uno_de_un_dia_pasado() {
        Usuario usuarioMedico = usuario(1L, Rol.MEDICO);
        Turno deHoyMasTarde = turno(5L, usuarioMedico, usuario(2L, Rol.PACIENTE), EstadoTurno.CONFIRMADO);
        deHoyMasTarde.setInicio(LocalDateTime.now().toLocalDate().atTime(23, 59));
        Turno deAyer = turno(6L, usuarioMedico, usuario(2L, Rol.PACIENTE), EstadoTurno.CONFIRMADO);
        deAyer.setInicio(LocalDateTime.now().minusDays(1));

        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioMedico);
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(deHoyMasTarde));
        when(turnoRepository.findById(6L)).thenReturn(Optional.of(deAyer));
        when(consultaRepository.findByTurnoId(any())).thenReturn(Optional.empty());
        when(consultaRepository.save(any(Consulta.class))).thenAnswer(inv -> {
            Consulta c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });
        when(diagnosticoRepository.findByConsultaId(1L)).thenReturn(List.of());
        when(recetaRepository.findByConsultaId(1L)).thenReturn(List.of());
        when(ordenEstudioRepository.findByConsultaId(1L)).thenReturn(List.of());
        CrearConsultaRequest request = new CrearConsultaRequest("Notas", List.of(new DiagnosticoRequest("Gripe", null)));

        consultaService.crear(5L, request);
        consultaService.crear(6L, request);

        assertThat(deHoyMasTarde.getEstado()).isEqualTo(EstadoTurno.ATENDIDO);
        assertThat(deAyer.getEstado()).isEqualTo(EstadoTurno.ATENDIDO);
    }

    @Test
    void crear_rechaza_un_turno_que_no_esta_confirmado() {
        Usuario usuarioMedico = usuario(1L, Rol.MEDICO);
        Turno turno = turno(5L, usuarioMedico, usuario(2L, Rol.PACIENTE), EstadoTurno.RESERVADO);

        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioMedico);
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));

        CrearConsultaRequest request = new CrearConsultaRequest(null, List.of(new DiagnosticoRequest("Gripe", null)));
        assertStatus(() -> consultaService.crear(5L, request), HttpStatus.BAD_REQUEST);
    }

    @Test
    void crear_rechaza_un_turno_que_ya_tiene_consulta() {
        Usuario usuarioMedico = usuario(1L, Rol.MEDICO);
        Turno turno = turno(5L, usuarioMedico, usuario(2L, Rol.PACIENTE), EstadoTurno.CONFIRMADO);

        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioMedico);
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));
        when(consultaRepository.findByTurnoId(5L)).thenReturn(Optional.of(Consulta.builder().id(1L).turno(turno).build()));

        CrearConsultaRequest request = new CrearConsultaRequest(null, List.of(new DiagnosticoRequest("Gripe", null)));
        assertStatus(() -> consultaService.crear(5L, request), HttpStatus.CONFLICT);
    }

    @Test
    void crear_rechaza_a_un_medico_que_no_es_el_del_turno() {
        Turno turno = turno(5L, usuario(1L, Rol.MEDICO), usuario(2L, Rol.PACIENTE), EstadoTurno.CONFIRMADO);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuario(999L, Rol.MEDICO));
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));

        CrearConsultaRequest request = new CrearConsultaRequest(null, List.of(new DiagnosticoRequest("Gripe", null)));
        assertStatus(() -> consultaService.crear(5L, request), HttpStatus.FORBIDDEN);
    }

    @Test
    void crear_lo_permite_a_un_admin() {
        Turno turno = turno(5L, usuario(1L, Rol.MEDICO), usuario(2L, Rol.PACIENTE), EstadoTurno.CONFIRMADO);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuario(999L, Rol.ADMIN));
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));
        when(consultaRepository.findByTurnoId(5L)).thenReturn(Optional.empty());
        when(consultaRepository.save(any(Consulta.class))).thenAnswer(inv -> inv.getArgument(0));
        when(diagnosticoRepository.findByConsultaId(any())).thenReturn(List.of());
        when(recetaRepository.findByConsultaId(any())).thenReturn(List.of());
        when(ordenEstudioRepository.findByConsultaId(any())).thenReturn(List.of());

        CrearConsultaRequest request = new CrearConsultaRequest(null, List.of(new DiagnosticoRequest("Gripe", null)));
        assertThat(consultaService.crear(5L, request)).isNotNull();
    }

    @Test
    void obtenerPorTurno_lo_permite_al_paciente_dueno() {
        Usuario usuarioPaciente = usuario(2L, Rol.PACIENTE);
        Turno turno = turno(5L, usuario(1L, Rol.MEDICO), usuarioPaciente, EstadoTurno.ATENDIDO);
        Consulta consulta = Consulta.builder().id(1L).turno(turno).build();

        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente);
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));
        when(consultaRepository.findByTurnoId(5L)).thenReturn(Optional.of(consulta));
        when(diagnosticoRepository.findByConsultaId(1L)).thenReturn(List.of());
        when(recetaRepository.findByConsultaId(1L)).thenReturn(List.of());
        when(ordenEstudioRepository.findByConsultaId(1L)).thenReturn(List.of());

        assertThat(consultaService.obtenerPorTurno(5L).id()).isEqualTo(1L);
    }

    @Test
    void obtenerPorTurno_rechaza_a_otro_paciente() {
        Turno turno = turno(5L, usuario(1L, Rol.MEDICO), usuario(2L, Rol.PACIENTE), EstadoTurno.ATENDIDO);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuario(888L, Rol.PACIENTE));
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));

        assertStatus(() -> consultaService.obtenerPorTurno(5L), HttpStatus.FORBIDDEN);
    }

    @Test
    void obtenerPorTurno_404_si_no_hay_consulta_cargada() {
        Usuario usuarioPaciente = usuario(2L, Rol.PACIENTE);
        Turno turno = turno(5L, usuario(1L, Rol.MEDICO), usuarioPaciente, EstadoTurno.CONFIRMADO);

        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente);
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));
        when(consultaRepository.findByTurnoId(5L)).thenReturn(Optional.empty());

        assertStatus(() -> consultaService.obtenerPorTurno(5L), HttpStatus.NOT_FOUND);
    }
}
