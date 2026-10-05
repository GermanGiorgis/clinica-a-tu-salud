package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.PacienteResponse;
import com.germanggiorgis.clinica.model.Paciente;
import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Usuario;
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
class PacienteServiceTest {

    @Mock
    private PacienteRepository pacienteRepository;

    @InjectMocks
    private PacienteService pacienteService;

    private Paciente paciente(long id, String dni, boolean verificado, boolean cuentaActiva) {
        Usuario usuario = Usuario.builder().id(100 + id).email("p" + id + "@clinica.test").rol(Rol.PACIENTE)
                .nombre("Nombre" + id).apellido("Apellido").activo(cuentaActiva).build();
        return Paciente.builder().id(id).usuario(usuario).dni(dni).fechaNacimiento(LocalDate.of(1990, 1, 1))
                .identidadVerificada(verificado).build();
    }

    @Test
    void pendientes_lista_las_cuentas_activas_sin_verificar_con_los_datos_para_comparar_con_el_documento() {
        when(pacienteRepository.findByIdentidadVerificadaFalseOrderByIdAsc())
                .thenReturn(List.of(paciente(1L, "30111222", false, true)));

        List<PacienteResponse> pendientes = pacienteService.pendientes();

        assertThat(pendientes).hasSize(1);
        PacienteResponse p = pendientes.get(0);
        assertThat(p.dni()).isEqualTo("30111222");
        assertThat(p.nombre()).isEqualTo("Nombre1");
        assertThat(p.email()).isEqualTo("p1@clinica.test");
        assertThat(p.fechaNacimiento()).isEqualTo(LocalDate.of(1990, 1, 1));
        assertThat(p.identidadVerificada()).isFalse();
    }

    @Test
    void pendientes_oculta_las_cuentas_que_el_admin_ya_desactivo() {
        when(pacienteRepository.findByIdentidadVerificadaFalseOrderByIdAsc()).thenReturn(List.of(
                paciente(1L, "30111222", false, true),
                paciente(2L, "30111222", false, false)));

        assertThat(pacienteService.pendientes()).extracting(PacienteResponse::id).containsExactly(1L);
    }

    @Test
    void verificar_marca_la_identidad_como_verificada() {
        Paciente p = paciente(1L, "30111222", false, true);
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(p));
        when(pacienteRepository.existsByDniAndIdentidadVerificadaTrue("30111222")).thenReturn(false);
        when(pacienteRepository.save(any(Paciente.class))).thenAnswer(inv -> inv.getArgument(0));

        PacienteResponse respuesta = pacienteService.verificar(1L);

        assertThat(respuesta.identidadVerificada()).isTrue();
        assertThat(p.isIdentidadVerificada()).isTrue();
    }

    @Test
    void verificar_rechaza_con_409_si_otro_paciente_ya_verifico_ese_mismo_dni() {
        Paciente intruso = paciente(2L, "30111222", false, true);
        when(pacienteRepository.findById(2L)).thenReturn(Optional.of(intruso));
        when(pacienteRepository.existsByDniAndIdentidadVerificadaTrue("30111222")).thenReturn(true);

        assertStatus(() -> pacienteService.verificar(2L), HttpStatus.CONFLICT);

        assertThat(intruso.isIdentidadVerificada()).isFalse();
        verify(pacienteRepository, never()).save(any(Paciente.class));
    }

    @Test
    void verificar_a_uno_ya_verificado_no_cambia_nada() {
        Paciente p = paciente(1L, "30111222", true, true);
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(p));

        assertThat(pacienteService.verificar(1L).identidadVerificada()).isTrue();

        verify(pacienteRepository, never()).save(any(Paciente.class));
        verify(pacienteRepository, never()).existsByDniAndIdentidadVerificadaTrue(any());
    }

    @Test
    void verificar_un_paciente_inexistente_es_404() {
        when(pacienteRepository.findById(9L)).thenReturn(Optional.empty());

        assertStatus(() -> pacienteService.verificar(9L), HttpStatus.NOT_FOUND);
    }
}
