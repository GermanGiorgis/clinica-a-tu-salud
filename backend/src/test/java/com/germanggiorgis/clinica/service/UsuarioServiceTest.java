package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.CrearUsuarioRequest;
import com.germanggiorgis.clinica.dto.UsuarioResponse;
import com.germanggiorgis.clinica.model.Especialidad;
import com.germanggiorgis.clinica.model.Medico;
import com.germanggiorgis.clinica.model.Paciente;
import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Usuario;
import com.germanggiorgis.clinica.repository.EspecialidadRepository;
import com.germanggiorgis.clinica.repository.MedicoRepository;
import com.germanggiorgis.clinica.repository.PacienteRepository;
import com.germanggiorgis.clinica.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.germanggiorgis.clinica.service.HttpStatusAssert.assertStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PacienteRepository pacienteRepository;
    @Mock
    private MedicoRepository medicoRepository;
    @Mock
    private EspecialidadRepository especialidadRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UsuarioContextService usuarioContextService;

    @InjectMocks
    private UsuarioService usuarioService;

    private CrearUsuarioRequest paciente(String email, String dni) {
        return new CrearUsuarioRequest(Rol.PACIENTE, "Nombre", "Apellido", email, "clave123",
                null, dni, LocalDate.of(1990, 1, 1), null, null);
    }

    private CrearUsuarioRequest medico(String email, String matricula, List<Long> especialidadIds) {
        return new CrearUsuarioRequest(Rol.MEDICO, "Nombre", "Apellido", email, "clave123",
                null, null, null, matricula, especialidadIds);
    }

    private CrearUsuarioRequest admin(String email) {
        return new CrearUsuarioRequest(Rol.ADMIN, "Nombre", "Apellido", email, "clave123",
                null, null, null, null, null);
    }

    @Test
    void crear_rechaza_email_duplicado() {
        when(usuarioRepository.existsByEmailIgnoreCase("x@clinica.test")).thenReturn(true);
        assertStatus(() -> usuarioService.crear(paciente("x@clinica.test", "1")), HttpStatus.CONFLICT);
    }

    @Test
    void crear_paciente_completo_devuelve_el_id_del_perfil() {
        CrearUsuarioRequest request = paciente("juan@clinica.test", "30111222");
        when(usuarioRepository.existsByEmailIgnoreCase("juan@clinica.test")).thenReturn(false);
        when(passwordEncoder.encode("clave123")).thenReturn("hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(pacienteRepository.existsByDniAndIdentidadVerificadaTrue("30111222")).thenReturn(false);
        when(pacienteRepository.save(any(Paciente.class))).thenAnswer(inv -> {
            Paciente p = inv.getArgument(0);
            p.setId(50L);
            return p;
        });

        UsuarioResponse respuesta = usuarioService.crear(request);

        assertThat(respuesta.rol()).isEqualTo(Rol.PACIENTE);
        assertThat(respuesta.perfilId()).isEqualTo(50L);
    }

    @Test
    void crear_paciente_sin_dni_es_rechazado() {
        CrearUsuarioRequest request = new CrearUsuarioRequest(Rol.PACIENTE, "N", "A", "x@clinica.test", "clave123",
                null, null, LocalDate.of(1990, 1, 1), null, null);
        when(usuarioRepository.existsByEmailIgnoreCase("x@clinica.test")).thenReturn(false);
        when(passwordEncoder.encode("clave123")).thenReturn("hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        assertStatus(() -> usuarioService.crear(request), HttpStatus.BAD_REQUEST);
    }

    @Test
    void crear_paciente_con_dni_duplicado_es_rechazado() {
        CrearUsuarioRequest request = paciente("juan@clinica.test", "30111222");
        when(usuarioRepository.existsByEmailIgnoreCase("juan@clinica.test")).thenReturn(false);
        when(passwordEncoder.encode("clave123")).thenReturn("hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pacienteRepository.existsByDniAndIdentidadVerificadaTrue("30111222")).thenReturn(true);

        assertStatus(() -> usuarioService.crear(request), HttpStatus.CONFLICT);
    }

    @Test
    void crear_paciente_lo_deja_con_la_identidad_verificada_porque_lo_da_de_alta_el_personal() {
        when(usuarioRepository.existsByEmailIgnoreCase("juan@clinica.test")).thenReturn(false);
        when(passwordEncoder.encode("clave123")).thenReturn("hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pacienteRepository.existsByDniAndIdentidadVerificadaTrue("30111222")).thenReturn(false);
        when(pacienteRepository.save(any(Paciente.class))).thenAnswer(inv -> inv.getArgument(0));

        usuarioService.crear(paciente("juan@clinica.test", "30111222"));

        ArgumentCaptor<Paciente> guardado = ArgumentCaptor.forClass(Paciente.class);
        verify(pacienteRepository).save(guardado.capture());
        assertThat(guardado.getValue().isIdentidadVerificada()).isTrue();
    }

    // --- activar / desactivar cuentas ---

    private Usuario usuarioConId(long id, Rol rol) {
        return Usuario.builder().id(id).email("u" + id + "@clinica.test").rol(rol).nombre("N").apellido("A").activo(true).build();
    }

    @Test
    void cambiarActivo_desactiva_la_cuenta_de_otro_usuario() {
        Usuario admin = usuarioConId(1L, Rol.ADMIN);
        Usuario medico = usuarioConId(2L, Rol.MEDICO);
        when(usuarioContextService.getUsuarioActual()).thenReturn(admin);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(medico));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        UsuarioResponse respuesta = usuarioService.cambiarActivo(2L, false);

        assertThat(respuesta.activo()).isFalse();
        assertThat(medico.isActivo()).isFalse();
    }

    @Test
    void cambiarActivo_reactiva_una_cuenta_desactivada() {
        Usuario admin = usuarioConId(1L, Rol.ADMIN);
        Usuario medico = usuarioConId(2L, Rol.MEDICO);
        medico.setActivo(false);
        when(usuarioContextService.getUsuarioActual()).thenReturn(admin);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(medico));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(usuarioService.cambiarActivo(2L, true).activo()).isTrue();
    }

    @Test
    void cambiarActivo_no_deja_que_un_admin_desactive_su_propia_cuenta() {
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioConId(1L, Rol.ADMIN));

        assertStatus(() -> usuarioService.cambiarActivo(1L, false), HttpStatus.BAD_REQUEST);

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void cambiarActivo_de_un_usuario_inexistente_es_404() {
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioConId(1L, Rol.ADMIN));
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertStatus(() -> usuarioService.cambiarActivo(99L, false), HttpStatus.NOT_FOUND);
    }

    @Test
    void crear_medico_con_especialidades_devuelve_el_id_del_perfil() {
        CrearUsuarioRequest request = medico("dra@clinica.test", "MP-1234", List.of(1L, 2L));
        when(usuarioRepository.existsByEmailIgnoreCase("dra@clinica.test")).thenReturn(false);
        when(passwordEncoder.encode("clave123")).thenReturn("hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(medicoRepository.findByMatricula("MP-1234")).thenReturn(Optional.empty());
        when(especialidadRepository.findById(1L)).thenReturn(Optional.of(Especialidad.builder().id(1L).nombre("Cardiología").build()));
        when(especialidadRepository.findById(2L)).thenReturn(Optional.of(Especialidad.builder().id(2L).nombre("Clínica médica").build()));
        when(medicoRepository.save(any(Medico.class))).thenAnswer(inv -> {
            Medico m = inv.getArgument(0);
            m.setId(7L);
            return m;
        });

        UsuarioResponse respuesta = usuarioService.crear(request);

        assertThat(respuesta.perfilId()).isEqualTo(7L);
    }

    @Test
    void crear_medico_sin_matricula_es_rechazado() {
        CrearUsuarioRequest request = medico("dra@clinica.test", null, null);
        when(usuarioRepository.existsByEmailIgnoreCase("dra@clinica.test")).thenReturn(false);
        when(passwordEncoder.encode("clave123")).thenReturn("hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        assertStatus(() -> usuarioService.crear(request), HttpStatus.BAD_REQUEST);
    }

    @Test
    void crear_medico_con_matricula_duplicada_es_rechazado() {
        CrearUsuarioRequest request = medico("dra@clinica.test", "MP-1234", null);
        when(usuarioRepository.existsByEmailIgnoreCase("dra@clinica.test")).thenReturn(false);
        when(passwordEncoder.encode("clave123")).thenReturn("hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(medicoRepository.findByMatricula("MP-1234")).thenReturn(Optional.of(Medico.builder().id(1L).matricula("MP-1234").build()));

        assertStatus(() -> usuarioService.crear(request), HttpStatus.CONFLICT);
    }

    @Test
    void crear_medico_con_especialidad_inexistente_es_rechazado() {
        CrearUsuarioRequest request = medico("dra@clinica.test", "MP-1234", List.of(99L));
        when(usuarioRepository.existsByEmailIgnoreCase("dra@clinica.test")).thenReturn(false);
        when(passwordEncoder.encode("clave123")).thenReturn("hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(medicoRepository.findByMatricula("MP-1234")).thenReturn(Optional.empty());
        when(especialidadRepository.findById(99L)).thenReturn(Optional.empty());

        assertStatus(() -> usuarioService.crear(request), HttpStatus.NOT_FOUND);
    }

    @Test
    void crear_admin_no_tiene_perfil_propio() {
        CrearUsuarioRequest request = admin("admin2@clinica.test");
        when(usuarioRepository.existsByEmailIgnoreCase("admin2@clinica.test")).thenReturn(false);
        when(passwordEncoder.encode("clave123")).thenReturn("hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        UsuarioResponse respuesta = usuarioService.crear(request);

        assertThat(respuesta.perfilId()).isNull();
    }

    @Test
    void listar_devuelve_todos_los_usuarios() {
        when(usuarioRepository.findAll()).thenReturn(List.of(
                Usuario.builder().id(1L).email("a@clinica.test").rol(Rol.ADMIN).nombre("A").apellido("B").activo(true).build()));

        assertThat(usuarioService.listar()).hasSize(1);
    }
}
