package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.EspecialidadRequest;
import com.germanggiorgis.clinica.dto.EspecialidadResponse;
import com.germanggiorgis.clinica.model.Especialidad;
import com.germanggiorgis.clinica.repository.EspecialidadRepository;
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
class EspecialidadServiceTest {

    @Mock
    private EspecialidadRepository especialidadRepository;

    @InjectMocks
    private EspecialidadService especialidadService;

    @Test
    void listar_devuelve_todas() {
        when(especialidadRepository.findAll()).thenReturn(List.of(Especialidad.builder().id(1L).nombre("Cardiología").build()));
        assertThat(especialidadService.listar()).hasSize(1);
    }

    @Test
    void crear_guarda_una_especialidad_nueva() {
        when(especialidadRepository.existsByNombreIgnoreCase("Cardiología")).thenReturn(false);
        when(especialidadRepository.save(any(Especialidad.class))).thenAnswer(inv -> {
            Especialidad e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });

        EspecialidadResponse respuesta = especialidadService.crear(new EspecialidadRequest("Cardiología", "Corazón"));

        assertThat(respuesta.id()).isEqualTo(1L);
        assertThat(respuesta.nombre()).isEqualTo("Cardiología");
    }

    @Test
    void crear_rechaza_un_nombre_duplicado() {
        when(especialidadRepository.existsByNombreIgnoreCase("Cardiología")).thenReturn(true);
        assertStatus(() -> especialidadService.crear(new EspecialidadRequest("Cardiología", null)), HttpStatus.CONFLICT);
    }

    @Test
    void actualizar_modifica_los_datos() {
        Especialidad existente = Especialidad.builder().id(1L).nombre("Cardio").build();
        when(especialidadRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(especialidadRepository.existsByNombreIgnoreCaseAndIdNot("Cardiología", 1L)).thenReturn(false);
        when(especialidadRepository.save(any(Especialidad.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(especialidadService.actualizar(1L, new EspecialidadRequest("Cardiología", null)).nombre()).isEqualTo("Cardiología");
    }

    @Test
    void actualizar_rechaza_el_nombre_de_otra_especialidad() {
        when(especialidadRepository.findById(1L)).thenReturn(Optional.of(Especialidad.builder().id(1L).nombre("Cardio").build()));
        when(especialidadRepository.existsByNombreIgnoreCaseAndIdNot("Clínica", 1L)).thenReturn(true);

        assertStatus(() -> especialidadService.actualizar(1L, new EspecialidadRequest("Clínica", null)), HttpStatus.CONFLICT);
    }

    @Test
    void actualizar_rechaza_una_inexistente() {
        when(especialidadRepository.findById(1L)).thenReturn(Optional.empty());
        assertStatus(() -> especialidadService.actualizar(1L, new EspecialidadRequest("X", null)), HttpStatus.NOT_FOUND);
    }

    @Test
    void eliminar_borra_la_especialidad() {
        Especialidad existente = Especialidad.builder().id(1L).nombre("Cardio").build();
        when(especialidadRepository.findById(1L)).thenReturn(Optional.of(existente));

        especialidadService.eliminar(1L);

        verify(especialidadRepository).delete(existente);
    }

    @Test
    void eliminar_rechaza_una_inexistente() {
        when(especialidadRepository.findById(1L)).thenReturn(Optional.empty());
        assertStatus(() -> especialidadService.eliminar(1L), HttpStatus.NOT_FOUND);
    }
}
