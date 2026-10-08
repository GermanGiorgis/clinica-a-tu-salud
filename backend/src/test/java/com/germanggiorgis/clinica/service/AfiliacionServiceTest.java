package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.AfiliacionResponse;
import com.germanggiorgis.clinica.dto.CrearAfiliacionRequest;
import com.germanggiorgis.clinica.model.Afiliacion;
import com.germanggiorgis.clinica.model.ObraSocial;
import com.germanggiorgis.clinica.model.Paciente;
import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Usuario;
import com.germanggiorgis.clinica.repository.AfiliacionRepository;
import com.germanggiorgis.clinica.repository.ObraSocialRepository;
import com.germanggiorgis.clinica.repository.PacienteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

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
class AfiliacionServiceTest {

    @Mock
    private AfiliacionRepository afiliacionRepository;
    @Mock
    private ObraSocialRepository obraSocialRepository;
    @Mock
    private PacienteRepository pacienteRepository;
    @Mock
    private UsuarioContextService usuarioContextService;

    @InjectMocks
    private AfiliacionService afiliacionService;

    private Usuario usuarioPaciente() {
        return Usuario.builder().id(2L).email("p@clinica.test").rol(Rol.PACIENTE).nombre("P").apellido("P").activo(true).build();
    }

    private Paciente paciente(long id) {
        return Paciente.builder().id(id).usuario(usuarioPaciente()).dni("DNI" + id).build();
    }

    private ObraSocial obraSocial() {
        return ObraSocial.builder().id(1L).nombre("OSDE").build();
    }

    private Afiliacion afiliacion(long id, Paciente paciente) {
        return Afiliacion.builder().id(id).paciente(paciente).obraSocial(obraSocial()).numeroAfiliado("123")
                .vigenteDesde(LocalDate.of(2026, 1, 1)).build();
    }

    @Test
    void crear_rechaza_una_afiliacion_que_el_paciente_ya_cargo() {
        Paciente paciente = paciente(1L);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente());
        when(pacienteRepository.findByUsuarioId(2L)).thenReturn(Optional.of(paciente));
        when(obraSocialRepository.findById(1L)).thenReturn(Optional.of(obraSocial()));
        when(afiliacionRepository.existsByPacienteIdAndObraSocialIdAndNumeroAfiliado(1L, 1L, "123")).thenReturn(true);

        // los espacios de más al copiar y pegar el número no la hacen "otra" afiliación
        var request = new CrearAfiliacionRequest(1L, "  123 ", null, LocalDate.of(2026, 1, 1), null);
        assertStatus(() -> afiliacionService.crear(request), HttpStatus.CONFLICT);
        verify(afiliacionRepository, never()).save(any());
    }

    @Test
    void crear_rechaza_una_vigencia_que_termina_antes_de_empezar() {
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente());
        when(pacienteRepository.findByUsuarioId(2L)).thenReturn(Optional.of(paciente(1L)));
        when(obraSocialRepository.findById(1L)).thenReturn(Optional.of(obraSocial()));

        var request = new CrearAfiliacionRequest(1L, "123", null, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 5, 31));
        assertStatus(() -> afiliacionService.crear(request), HttpStatus.BAD_REQUEST);
        verify(afiliacionRepository, never()).save(any());
    }

    @Test
    void crear_acepta_una_vigencia_de_un_solo_dia() {
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente());
        when(pacienteRepository.findByUsuarioId(2L)).thenReturn(Optional.of(paciente(1L)));
        when(obraSocialRepository.findById(1L)).thenReturn(Optional.of(obraSocial()));
        when(afiliacionRepository.save(any(Afiliacion.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new CrearAfiliacionRequest(1L, "123", null, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1));
        assertThat(afiliacionService.crear(request)).isNotNull();
    }

    @Test
    void misAfiliaciones_devuelve_solo_las_del_paciente_logueado() {
        Paciente paciente = paciente(1L);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente());
        when(pacienteRepository.findByUsuarioId(2L)).thenReturn(Optional.of(paciente));
        when(afiliacionRepository.findByPacienteIdOrderByVigenteDesdeDesc(1L)).thenReturn(List.of(afiliacion(1L, paciente)));

        List<AfiliacionResponse> respuesta = afiliacionService.misAfiliaciones();

        assertThat(respuesta).hasSize(1);
        assertThat(respuesta.get(0).obraSocialNombre()).isEqualTo("OSDE");
    }

    @Test
    void crear_asocia_la_afiliacion_al_paciente_logueado() {
        Paciente paciente = paciente(1L);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente());
        when(pacienteRepository.findByUsuarioId(2L)).thenReturn(Optional.of(paciente));
        when(obraSocialRepository.findById(1L)).thenReturn(Optional.of(obraSocial()));
        when(afiliacionRepository.save(any(Afiliacion.class))).thenAnswer(inv -> {
            Afiliacion a = inv.getArgument(0);
            a.setId(10L);
            return a;
        });

        AfiliacionResponse respuesta = afiliacionService.crear(
                new CrearAfiliacionRequest(1L, "123456", "210", LocalDate.of(2026, 1, 1), null));

        assertThat(respuesta.id()).isEqualTo(10L);
        assertThat(respuesta.numeroAfiliado()).isEqualTo("123456");
    }

    @Test
    void crear_rechaza_una_obra_social_inexistente() {
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente());
        when(pacienteRepository.findByUsuarioId(2L)).thenReturn(Optional.of(paciente(1L)));
        when(obraSocialRepository.findById(99L)).thenReturn(Optional.empty());

        assertStatus(() -> afiliacionService.crear(
                new CrearAfiliacionRequest(99L, "1", null, LocalDate.now(), null)), HttpStatus.NOT_FOUND);
    }

    @Test
    void crear_rechaza_a_un_usuario_sin_perfil_de_paciente() {
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente());
        when(pacienteRepository.findByUsuarioId(2L)).thenReturn(Optional.empty());

        assertStatus(() -> afiliacionService.crear(
                new CrearAfiliacionRequest(1L, "1", null, LocalDate.now(), null)), HttpStatus.NOT_FOUND);
    }

    @Test
    void eliminar_borra_una_afiliacion_propia() {
        Paciente paciente = paciente(1L);
        Afiliacion afiliacion = afiliacion(10L, paciente);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente());
        when(pacienteRepository.findByUsuarioId(2L)).thenReturn(Optional.of(paciente));
        when(afiliacionRepository.findById(10L)).thenReturn(Optional.of(afiliacion));

        afiliacionService.eliminar(10L);

        verify(afiliacionRepository).delete(afiliacion);
    }

    @Test
    void eliminar_rechaza_la_afiliacion_de_otro_paciente() {
        Paciente yo = paciente(1L);
        Paciente otro = paciente(2L);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente());
        when(pacienteRepository.findByUsuarioId(2L)).thenReturn(Optional.of(yo));
        when(afiliacionRepository.findById(10L)).thenReturn(Optional.of(afiliacion(10L, otro)));

        assertStatus(() -> afiliacionService.eliminar(10L), HttpStatus.FORBIDDEN);
    }

    @Test
    void eliminar_rechaza_una_afiliacion_inexistente() {
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente());
        when(pacienteRepository.findByUsuarioId(2L)).thenReturn(Optional.of(paciente(1L)));
        when(afiliacionRepository.findById(10L)).thenReturn(Optional.empty());

        assertStatus(() -> afiliacionService.eliminar(10L), HttpStatus.NOT_FOUND);
    }
}
