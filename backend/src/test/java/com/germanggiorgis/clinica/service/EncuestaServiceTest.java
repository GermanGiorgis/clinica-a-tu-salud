package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.CrearEncuestaRequest;
import com.germanggiorgis.clinica.dto.EncuestaResponse;
import com.germanggiorgis.clinica.model.Consultorio;
import com.germanggiorgis.clinica.model.Encuesta;
import com.germanggiorgis.clinica.model.EstadoTurno;
import com.germanggiorgis.clinica.model.Medico;
import com.germanggiorgis.clinica.model.Paciente;
import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Turno;
import com.germanggiorgis.clinica.model.Usuario;
import com.germanggiorgis.clinica.repository.EncuestaRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EncuestaServiceTest {

    @Mock
    private EncuestaRepository encuestaRepository;
    @Mock
    private TurnoRepository turnoRepository;
    @Mock
    private UsuarioContextService usuarioContextService;

    @InjectMocks
    private EncuestaService encuestaService;

    private Usuario usuario(long id, Rol rol) {
        return Usuario.builder().id(id).email("u" + id + "@clinica.test").rol(rol).nombre("N").apellido("A").activo(true).build();
    }

    private Turno turno(Usuario medicoUsuario, Usuario pacienteUsuario, EstadoTurno estado) {
        Medico medico = Medico.builder().id(1L).usuario(medicoUsuario).matricula("MP-1").especialidades(List.of()).build();
        Paciente paciente = Paciente.builder().id(1L).usuario(pacienteUsuario).dni("1").build();
        return Turno.builder().id(5L).medico(medico).paciente(paciente)
                .consultorio(Consultorio.builder().id(1L).nombre("C").build())
                .creadoPor(pacienteUsuario).inicio(LocalDateTime.now()).fin(LocalDateTime.now().plusMinutes(30)).estado(estado).build();
    }

    @Test
    void crear_lo_permite_al_paciente_dueno_de_un_turno_atendido() {
        Usuario usuarioPaciente = usuario(2L, Rol.PACIENTE);
        Turno turno = turno(usuario(1L, Rol.MEDICO), usuarioPaciente, EstadoTurno.ATENDIDO);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente);
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));
        when(encuestaRepository.findByTurnoId(5L)).thenReturn(Optional.empty());
        when(encuestaRepository.save(any(Encuesta.class))).thenAnswer(inv -> {
            Encuesta e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });

        EncuestaResponse respuesta = encuestaService.crear(5L, new CrearEncuestaRequest(4, "Muy bien"));

        assertThat(respuesta.puntaje()).isEqualTo(4);
        assertThat(respuesta.comentario()).isEqualTo("Muy bien");
    }

    @Test
    void crear_rechaza_un_turno_que_no_esta_atendido() {
        Usuario usuarioPaciente = usuario(2L, Rol.PACIENTE);
        Turno turno = turno(usuario(1L, Rol.MEDICO), usuarioPaciente, EstadoTurno.RESERVADO);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente);
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));

        assertStatus(() -> encuestaService.crear(5L, new CrearEncuestaRequest(5, null)), HttpStatus.BAD_REQUEST);
    }

    @Test
    void crear_rechaza_a_un_paciente_que_no_es_el_del_turno() {
        Turno turno = turno(usuario(1L, Rol.MEDICO), usuario(2L, Rol.PACIENTE), EstadoTurno.ATENDIDO);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuario(999L, Rol.PACIENTE));
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));

        assertStatus(() -> encuestaService.crear(5L, new CrearEncuestaRequest(5, null)), HttpStatus.FORBIDDEN);
    }

    @Test
    void crear_rechaza_a_un_medico_aunque_el_turno_sea_suyo() {
        Usuario usuarioMedico = usuario(1L, Rol.MEDICO);
        Turno turno = turno(usuarioMedico, usuario(2L, Rol.PACIENTE), EstadoTurno.ATENDIDO);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioMedico);
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));

        assertStatus(() -> encuestaService.crear(5L, new CrearEncuestaRequest(5, null)), HttpStatus.FORBIDDEN);
    }

    @Test
    void crear_rechaza_una_segunda_encuesta_para_el_mismo_turno() {
        Usuario usuarioPaciente = usuario(2L, Rol.PACIENTE);
        Turno turno = turno(usuario(1L, Rol.MEDICO), usuarioPaciente, EstadoTurno.ATENDIDO);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente);
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));
        when(encuestaRepository.findByTurnoId(5L)).thenReturn(Optional.of(Encuesta.builder().id(1L).turno(turno).puntaje(3).build()));

        assertStatus(() -> encuestaService.crear(5L, new CrearEncuestaRequest(5, null)), HttpStatus.CONFLICT);
    }

    @Test
    void obtenerPorTurno_lo_permite_al_medico_del_turno() {
        Usuario usuarioMedico = usuario(1L, Rol.MEDICO);
        Turno turno = turno(usuarioMedico, usuario(2L, Rol.PACIENTE), EstadoTurno.ATENDIDO);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioMedico);
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));
        when(encuestaRepository.findByTurnoId(5L)).thenReturn(Optional.of(Encuesta.builder().id(1L).turno(turno).puntaje(3).build()));

        assertThat(encuestaService.obtenerPorTurno(5L).puntaje()).isEqualTo(3);
    }

    @Test
    void obtenerPorTurno_rechaza_a_un_tercero() {
        Turno turno = turno(usuario(1L, Rol.MEDICO), usuario(2L, Rol.PACIENTE), EstadoTurno.ATENDIDO);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuario(999L, Rol.PACIENTE));
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));

        assertStatus(() -> encuestaService.obtenerPorTurno(5L), HttpStatus.FORBIDDEN);
    }

    @Test
    void obtenerPorTurno_404_si_todavia_no_hay_encuesta() {
        Usuario usuarioPaciente = usuario(2L, Rol.PACIENTE);
        Turno turno = turno(usuario(1L, Rol.MEDICO), usuarioPaciente, EstadoTurno.ATENDIDO);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente);
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));
        when(encuestaRepository.findByTurnoId(5L)).thenReturn(Optional.empty());

        assertStatus(() -> encuestaService.obtenerPorTurno(5L), HttpStatus.NOT_FOUND);
    }
}
