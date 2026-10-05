package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.AccesoHistorialResponse;
import com.germanggiorgis.clinica.model.AccesoHistorial;
import com.germanggiorgis.clinica.model.Paciente;
import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Usuario;
import com.germanggiorgis.clinica.repository.AccesoHistorialRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditoriaServiceTest {

    @Mock
    private AccesoHistorialRepository accesoHistorialRepository;

    @InjectMocks
    private AuditoriaService auditoriaService;

    private AccesoHistorial acceso() {
        Usuario medico = Usuario.builder().id(11L).email("dra@clinica.test").rol(Rol.MEDICO).nombre("Laura").apellido("Lopez").activo(true).build();
        Usuario usuarioPaciente = Usuario.builder().id(12L).email("pablo@clinica.test").rol(Rol.PACIENTE).nombre("Pablo").apellido("Paz").activo(true).build();
        Paciente paciente = Paciente.builder().id(3L).usuario(usuarioPaciente).dni("1").fechaNacimiento(LocalDate.of(1990, 1, 1)).build();
        return AccesoHistorial.builder().id(1L).usuario(medico).paciente(paciente)
                .accedidoEn(LocalDateTime.of(2026, 10, 5, 12, 0)).build();
    }

    @Test
    void sin_filtro_devuelve_los_accesos_mas_recientes_de_toda_la_clinica() {
        when(accesoHistorialRepository.findTop200ByOrderByAccedidoEnDesc()).thenReturn(List.of(acceso()));

        List<AccesoHistorialResponse> accesos = auditoriaService.accesosHistorial(null);

        assertThat(accesos).hasSize(1);
        AccesoHistorialResponse a = accesos.get(0);
        assertThat(a.usuarioEmail()).isEqualTo("dra@clinica.test");
        assertThat(a.usuarioNombre()).isEqualTo("Laura Lopez");
        assertThat(a.usuarioRol()).isEqualTo(Rol.MEDICO);
        assertThat(a.pacienteId()).isEqualTo(3L);
        assertThat(a.pacienteNombre()).isEqualTo("Pablo Paz");
        assertThat(a.accedidoEn()).isEqualTo(LocalDateTime.of(2026, 10, 5, 12, 0));
    }

    @Test
    void con_un_paciente_devuelve_solo_los_accesos_a_su_historial() {
        when(accesoHistorialRepository.findTop200ByPacienteIdOrderByAccedidoEnDesc(3L)).thenReturn(List.of(acceso()));

        assertThat(auditoriaService.accesosHistorial(3L)).hasSize(1);

        verify(accesoHistorialRepository, never()).findTop200ByOrderByAccedidoEnDesc();
    }
}
