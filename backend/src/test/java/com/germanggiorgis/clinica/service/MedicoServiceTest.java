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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicoServiceTest {

    @Mock
    private MedicoRepository medicoRepository;
    @Mock
    private UsuarioContextService usuarioContextService;

    @InjectMocks
    private MedicoService medicoService;

    private Medico medico(long id, String nombre) {
        Usuario usuario = Usuario.builder().id(id).email(nombre + "@clinica.test").rol(Rol.MEDICO)
                .nombre(nombre).apellido("Apellido").activo(true).build();
        return Medico.builder().id(id).usuario(usuario).matricula("MP-" + id)
                .especialidades(List.of(Especialidad.builder().id(1L).nombre("Cardiología").build())).build();
    }

    private Usuario personal(Rol rol) {
        return Usuario.builder().id(99L).email("p@clinica.test").rol(rol).nombre("P").apellido("P").activo(true).build();
    }

    private Medico inactivo(long id) {
        Medico medico = medico(id, "Baja");
        medico.getUsuario().setActivo(false);
        return medico;
    }

    @Test
    void listar_sin_filtro_devuelve_solo_los_medicos_activos() {
        when(medicoRepository.findByUsuarioActivoTrue()).thenReturn(List.of(medico(1L, "Ana"), medico(2L, "Luis")));

        assertThat(medicoService.listar(null, false)).hasSize(2);
        verify(medicoRepository, never()).findAll();
    }

    @Test
    void listar_con_especialidad_filtra_por_esa_especialidad_y_solo_activos() {
        when(medicoRepository.findByEspecialidades_IdAndUsuarioActivoTrue(1L)).thenReturn(List.of(medico(1L, "Ana")));

        List<MedicoResponse> respuesta = medicoService.listar(1L, false);

        assertThat(respuesta).hasSize(1);
        assertThat(respuesta.get(0).nombre()).isEqualTo("Ana");
    }

    @Test
    void la_recepcion_y_el_admin_pueden_pedir_tambien_los_inactivos() {
        when(medicoRepository.findAll()).thenReturn(List.of(medico(1L, "Ana"), inactivo(2L)));
        when(usuarioContextService.getUsuarioActualSiHay()).thenReturn(Optional.of(personal(Rol.RECEPCIONISTA)));

        List<MedicoResponse> respuesta = medicoService.listar(null, true);

        assertThat(respuesta).hasSize(2);
        assertThat(respuesta).extracting(MedicoResponse::activo).containsExactly(true, false);
    }

    @Test
    void con_especialidad_la_recepcion_tambien_puede_pedir_los_inactivos() {
        when(medicoRepository.findByEspecialidades_Id(1L)).thenReturn(List.of(inactivo(2L)));
        when(usuarioContextService.getUsuarioActualSiHay()).thenReturn(Optional.of(personal(Rol.ADMIN)));

        assertThat(medicoService.listar(1L, true)).hasSize(1);
    }

    @Test
    void un_paciente_o_alguien_sin_sesion_no_consigue_los_inactivos_aunque_los_pida() {
        when(usuarioContextService.getUsuarioActualSiHay())
                .thenReturn(Optional.of(personal(Rol.PACIENTE)))
                .thenReturn(Optional.empty());
        when(medicoRepository.findByUsuarioActivoTrue()).thenReturn(List.of(medico(1L, "Ana")));

        assertThat(medicoService.listar(null, true)).hasSize(1);
        assertThat(medicoService.listar(null, true)).hasSize(1);
        verify(medicoRepository, never()).findAll();
    }

    @Test
    void sin_pedir_inactivos_ni_se_averigua_quien_pregunta() {
        when(medicoRepository.findByUsuarioActivoTrue()).thenReturn(List.of());

        medicoService.listar(null, false);

        verify(usuarioContextService, never()).getUsuarioActualSiHay();
    }

    @Test
    void obtener_devuelve_el_medico_con_sus_especialidades() {
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico(1L, "Ana")));

        assertThat(medicoService.obtener(1L).especialidades()).hasSize(1);
    }

    @Test
    void obtener_un_medico_inactivo_es_404_para_el_publico_pero_no_para_el_mostrador() {
        when(medicoRepository.findById(2L)).thenReturn(Optional.of(inactivo(2L)));
        when(usuarioContextService.getUsuarioActualSiHay()).thenReturn(Optional.empty());
        assertStatus(() -> medicoService.obtener(2L), HttpStatus.NOT_FOUND);

        when(usuarioContextService.getUsuarioActualSiHay()).thenReturn(Optional.of(personal(Rol.RECEPCIONISTA)));
        assertThat(medicoService.obtener(2L).activo()).isFalse();
    }

    @Test
    void obtener_rechaza_un_medico_inexistente() {
        when(medicoRepository.findById(1L)).thenReturn(Optional.empty());
        assertStatus(() -> medicoService.obtener(1L), HttpStatus.NOT_FOUND);
    }
}
