package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.PatologiaRequest;
import com.germanggiorgis.clinica.dto.PatologiaResponse;
import com.germanggiorgis.clinica.model.Especialidad;
import com.germanggiorgis.clinica.model.Patologia;
import com.germanggiorgis.clinica.repository.EspecialidadRepository;
import com.germanggiorgis.clinica.repository.PatologiaRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatologiaServiceTest {

    @Mock
    private PatologiaRepository patologiaRepository;
    @Mock
    private EspecialidadRepository especialidadRepository;

    @InjectMocks
    private PatologiaService patologiaService;

    private Especialidad dermatologia() {
        return Especialidad.builder().id(4L).nombre("Dermatología").build();
    }

    @Test
    void listar_devuelve_todas_con_su_especialidad() {
        when(patologiaRepository.findAll()).thenReturn(List.of(
                Patologia.builder().id(1L).nombre("Granito en la piel").especialidades(List.of(dermatologia())).build()));

        List<PatologiaResponse> respuesta = patologiaService.listar();

        assertThat(respuesta).hasSize(1);
        assertThat(respuesta.get(0).especialidades()).extracting(e -> e.nombre()).containsExactly("Dermatología");
    }

    @Test
    void crear_vincula_la_patologia_a_todas_sus_especialidades_sin_repetir() {
        when(patologiaRepository.existsByNombreIgnoreCase("Granito")).thenReturn(false);
        when(especialidadRepository.findById(4L)).thenReturn(Optional.of(dermatologia()));
        when(especialidadRepository.findById(5L)).thenReturn(Optional.of(Especialidad.builder().id(5L).nombre("Alergia").build()));
        when(patologiaRepository.save(any(Patologia.class))).thenAnswer(inv -> {
            Patologia p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        PatologiaResponse respuesta = patologiaService.crear(new PatologiaRequest("Granito", List.of(4L, 5L, 4L)));

        assertThat(respuesta.especialidades()).extracting(e -> e.id()).containsExactly(4L, 5L);
    }

    @Test
    void crear_rechaza_un_nombre_duplicado() {
        when(patologiaRepository.existsByNombreIgnoreCase("Granito")).thenReturn(true);
        assertStatus(() -> patologiaService.crear(new PatologiaRequest("Granito", List.of(4L))), HttpStatus.CONFLICT);
    }

    @Test
    void crear_rechaza_una_especialidad_inexistente() {
        when(patologiaRepository.existsByNombreIgnoreCase("Granito")).thenReturn(false);
        when(especialidadRepository.findById(4L)).thenReturn(Optional.of(dermatologia()));
        when(especialidadRepository.findById(99L)).thenReturn(Optional.empty());

        assertStatus(() -> patologiaService.crear(new PatologiaRequest("Granito", List.of(4L, 99L))), HttpStatus.NOT_FOUND);
    }

    @Test
    void actualizar_puede_cambiar_las_especialidades() {
        Patologia existente = Patologia.builder().id(1L).nombre("Granito").especialidades(List.of(dermatologia())).build();
        Especialidad otra = Especialidad.builder().id(5L).nombre("Clínica").build();
        when(patologiaRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(patologiaRepository.existsByNombreIgnoreCaseAndIdNot("Granito", 1L)).thenReturn(false);
        when(especialidadRepository.findById(5L)).thenReturn(Optional.of(otra));
        when(patologiaRepository.save(any(Patologia.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(patologiaService.actualizar(1L, new PatologiaRequest("Granito", List.of(5L))).especialidades())
                .extracting(e -> e.id()).containsExactly(5L);
    }

    @Test
    void actualizar_rechaza_el_nombre_de_otra_patologia() {
        when(patologiaRepository.findById(1L)).thenReturn(Optional.of(
                Patologia.builder().id(1L).nombre("A").especialidades(List.of(dermatologia())).build()));
        when(patologiaRepository.existsByNombreIgnoreCaseAndIdNot("B", 1L)).thenReturn(true);

        assertStatus(() -> patologiaService.actualizar(1L, new PatologiaRequest("B", List.of(4L))), HttpStatus.CONFLICT);
    }

    @Test
    void eliminar_borra_la_patologia() {
        Patologia existente = Patologia.builder().id(1L).nombre("A").especialidades(List.of(dermatologia())).build();
        when(patologiaRepository.findById(1L)).thenReturn(Optional.of(existente));

        patologiaService.eliminar(1L);

        verify(patologiaRepository).delete(existente);
    }

    @Test
    void eliminar_rechaza_una_inexistente() {
        when(patologiaRepository.findById(1L)).thenReturn(Optional.empty());
        assertStatus(() -> patologiaService.eliminar(1L), HttpStatus.NOT_FOUND);
    }
}
