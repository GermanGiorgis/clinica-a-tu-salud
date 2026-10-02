package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.MedicoResponse;
import com.germanggiorgis.clinica.model.Especialidad;
import com.germanggiorgis.clinica.model.Medico;
import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Usuario;
import com.germanggiorgis.clinica.repository.MedicoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static com.germanggiorgis.clinica.service.HttpStatusAssert.assertStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicoServiceTest {

    @Mock
    private MedicoRepository medicoRepository;

    @InjectMocks
    private MedicoService medicoService;

    private Medico medico(long id, String nombre) {
        Usuario usuario = Usuario.builder().id(id).email(nombre + "@clinica.test").rol(Rol.MEDICO)
                .nombre(nombre).apellido("Apellido").activo(true).build();
        return Medico.builder().id(id).usuario(usuario).matricula("MP-" + id)
                .especialidades(List.of(Especialidad.builder().id(1L).nombre("Cardiología").build())).build();
    }

    @Test
    void listar_sin_filtro_devuelve_todos_los_medicos() {
        when(medicoRepository.findAll()).thenReturn(List.of(medico(1L, "Ana"), medico(2L, "Luis")));

        assertThat(medicoService.listar(null)).hasSize(2);
    }

    @Test
    void listar_con_especialidad_filtra_por_esa_especialidad() {
        when(medicoRepository.findByEspecialidades_Id(1L)).thenReturn(List.of(medico(1L, "Ana")));

        List<MedicoResponse> respuesta = medicoService.listar(1L);

        assertThat(respuesta).hasSize(1);
        assertThat(respuesta.get(0).nombre()).isEqualTo("Ana");
    }

    @Test
    void obtener_devuelve_el_medico_con_sus_especialidades() {
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico(1L, "Ana")));

        assertThat(medicoService.obtener(1L).especialidades()).hasSize(1);
    }

    @Test
    void obtener_rechaza_un_medico_inexistente() {
        when(medicoRepository.findById(1L)).thenReturn(Optional.empty());
        assertStatus(() -> medicoService.obtener(1L), HttpStatus.NOT_FOUND);
    }
}
