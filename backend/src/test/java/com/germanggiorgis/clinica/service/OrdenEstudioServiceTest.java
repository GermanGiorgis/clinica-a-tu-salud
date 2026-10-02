package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.CargarResultadoRequest;
import com.germanggiorgis.clinica.dto.CrearOrdenEstudioRequest;
import com.germanggiorgis.clinica.dto.OrdenEstudioResponse;
import com.germanggiorgis.clinica.model.Consulta;
import com.germanggiorgis.clinica.model.Consultorio;
import com.germanggiorgis.clinica.model.EstadoOrdenEstudio;
import com.germanggiorgis.clinica.model.Estudio;
import com.germanggiorgis.clinica.model.Medico;
import com.germanggiorgis.clinica.model.OrdenEstudio;
import com.germanggiorgis.clinica.model.Paciente;
import com.germanggiorgis.clinica.model.ResultadoEstudio;
import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Turno;
import com.germanggiorgis.clinica.model.Usuario;
import com.germanggiorgis.clinica.repository.ConsultaRepository;
import com.germanggiorgis.clinica.repository.EstudioRepository;
import com.germanggiorgis.clinica.repository.OrdenEstudioRepository;
import com.germanggiorgis.clinica.repository.ResultadoEstudioRepository;
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
class OrdenEstudioServiceTest {

    @Mock
    private OrdenEstudioRepository ordenEstudioRepository;
    @Mock
    private ResultadoEstudioRepository resultadoEstudioRepository;
    @Mock
    private ConsultaRepository consultaRepository;
    @Mock
    private EstudioRepository estudioRepository;
    @Mock
    private UsuarioContextService usuarioContextService;

    @InjectMocks
    private OrdenEstudioService ordenEstudioService;

    private Usuario usuario(long id, Rol rol) {
        return Usuario.builder().id(id).email("u" + id + "@clinica.test").rol(rol).nombre("N").apellido("A").activo(true).build();
    }

    private Consulta consultaDe(Usuario medicoUsuario) {
        Medico medico = Medico.builder().id(1L).usuario(medicoUsuario).matricula("MP-1").especialidades(List.of()).build();
        Paciente paciente = Paciente.builder().id(1L).usuario(usuario(50L, Rol.PACIENTE)).dni("1").build();
        Turno turno = Turno.builder().id(1L).medico(medico).paciente(paciente)
                .consultorio(Consultorio.builder().id(1L).nombre("C").build())
                .creadoPor(medicoUsuario).inicio(LocalDateTime.now()).fin(LocalDateTime.now().plusMinutes(30)).build();
        return Consulta.builder().id(1L).turno(turno).build();
    }

    private OrdenEstudio orden(Consulta consulta, EstadoOrdenEstudio estado) {
        return OrdenEstudio.builder().id(1L).consulta(consulta)
                .estudio(Estudio.builder().id(1L).nombre("Hemograma").tipo("Laboratorio").build())
                .estado(estado).build();
    }

    @Test
    void crear_pide_un_estudio_en_estado_solicitada() {
        Usuario usuarioMedico = usuario(1L, Rol.MEDICO);
        Consulta consulta = consultaDe(usuarioMedico);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioMedico);
        when(consultaRepository.findById(1L)).thenReturn(Optional.of(consulta));
        when(estudioRepository.findById(1L)).thenReturn(Optional.of(Estudio.builder().id(1L).nombre("Hemograma").tipo("Laboratorio").build()));
        when(ordenEstudioRepository.save(any(OrdenEstudio.class))).thenAnswer(inv -> {
            OrdenEstudio o = inv.getArgument(0);
            o.setId(1L);
            return o;
        });

        OrdenEstudioResponse respuesta = ordenEstudioService.crear(1L, new CrearOrdenEstudioRequest(1L, "En ayunas"));

        assertThat(respuesta.estado()).isEqualTo(EstadoOrdenEstudio.SOLICITADA);
        assertThat(respuesta.resultado()).isNull();
    }

    @Test
    void crear_rechaza_a_un_medico_que_no_es_el_del_turno() {
        Consulta consulta = consultaDe(usuario(1L, Rol.MEDICO));
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuario(999L, Rol.MEDICO));
        when(consultaRepository.findById(1L)).thenReturn(Optional.of(consulta));

        assertStatus(() -> ordenEstudioService.crear(1L, new CrearOrdenEstudioRequest(1L, null)), HttpStatus.FORBIDDEN);
    }

    @Test
    void crear_rechaza_a_un_paciente() {
        Consulta consulta = consultaDe(usuario(1L, Rol.MEDICO));
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuario(50L, Rol.PACIENTE));
        when(consultaRepository.findById(1L)).thenReturn(Optional.of(consulta));

        assertStatus(() -> ordenEstudioService.crear(1L, new CrearOrdenEstudioRequest(1L, null)), HttpStatus.FORBIDDEN);
    }

    @Test
    void crear_rechaza_un_estudio_inexistente() {
        Usuario usuarioMedico = usuario(1L, Rol.MEDICO);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioMedico);
        when(consultaRepository.findById(1L)).thenReturn(Optional.of(consultaDe(usuarioMedico)));
        when(estudioRepository.findById(99L)).thenReturn(Optional.empty());

        assertStatus(() -> ordenEstudioService.crear(1L, new CrearOrdenEstudioRequest(99L, null)), HttpStatus.NOT_FOUND);
    }

    @Test
    void cargarResultado_completa_la_orden() {
        Usuario usuarioMedico = usuario(1L, Rol.MEDICO);
        OrdenEstudio orden = orden(consultaDe(usuarioMedico), EstadoOrdenEstudio.SOLICITADA);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioMedico);
        when(ordenEstudioRepository.findById(1L)).thenReturn(Optional.of(orden));
        when(resultadoEstudioRepository.save(any(ResultadoEstudio.class))).thenAnswer(inv -> {
            ResultadoEstudio r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });
        when(ordenEstudioRepository.save(any(OrdenEstudio.class))).thenAnswer(inv -> inv.getArgument(0));

        OrdenEstudioResponse respuesta = ordenEstudioService.cargarResultado(1L, new CargarResultadoRequest("Todo normal"));

        assertThat(respuesta.estado()).isEqualTo(EstadoOrdenEstudio.COMPLETADA);
        assertThat(respuesta.resultado().resultado()).isEqualTo("Todo normal");
    }

    @Test
    void cargarResultado_rechaza_una_orden_que_ya_tiene_resultado() {
        Usuario usuarioMedico = usuario(1L, Rol.MEDICO);
        OrdenEstudio orden = orden(consultaDe(usuarioMedico), EstadoOrdenEstudio.COMPLETADA);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioMedico);
        when(ordenEstudioRepository.findById(1L)).thenReturn(Optional.of(orden));

        assertStatus(() -> ordenEstudioService.cargarResultado(1L, new CargarResultadoRequest("otro")), HttpStatus.BAD_REQUEST);
    }

    @Test
    void cargarResultado_rechaza_a_un_medico_ajeno() {
        OrdenEstudio orden = orden(consultaDe(usuario(1L, Rol.MEDICO)), EstadoOrdenEstudio.SOLICITADA);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuario(999L, Rol.MEDICO));
        when(ordenEstudioRepository.findById(1L)).thenReturn(Optional.of(orden));

        assertStatus(() -> ordenEstudioService.cargarResultado(1L, new CargarResultadoRequest("x")), HttpStatus.FORBIDDEN);
    }

    @Test
    void cargarResultado_rechaza_una_orden_inexistente() {
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuario(1L, Rol.MEDICO));
        when(ordenEstudioRepository.findById(1L)).thenReturn(Optional.empty());

        assertStatus(() -> ordenEstudioService.cargarResultado(1L, new CargarResultadoRequest("x")), HttpStatus.NOT_FOUND);
    }
}
