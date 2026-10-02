package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.MotivoCancelacionRequest;
import com.germanggiorgis.clinica.dto.MotivoCancelacionResponse;
import com.germanggiorgis.clinica.model.MotivoCancelacion;
import com.germanggiorgis.clinica.repository.MotivoCancelacionRepository;
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
class MotivoCancelacionServiceTest {

    @Mock
    private MotivoCancelacionRepository motivoCancelacionRepository;

    @InjectMocks
    private MotivoCancelacionService motivoCancelacionService;

    @Test
    void listar_devuelve_todos() {
        when(motivoCancelacionRepository.findAll()).thenReturn(List.of(MotivoCancelacion.builder().id(1L).nombre("No puede asistir").build()));
        assertThat(motivoCancelacionService.listar()).hasSize(1);
    }

    @Test
    void crear_guarda_un_motivo_nuevo() {
        when(motivoCancelacionRepository.existsByNombreIgnoreCase("Enfermedad")).thenReturn(false);
        when(motivoCancelacionRepository.save(any(MotivoCancelacion.class))).thenAnswer(inv -> {
            MotivoCancelacion m = inv.getArgument(0);
            m.setId(1L);
            return m;
        });

        MotivoCancelacionResponse respuesta = motivoCancelacionService.crear(new MotivoCancelacionRequest("Enfermedad", null));

        assertThat(respuesta.id()).isEqualTo(1L);
    }

    @Test
    void crear_rechaza_un_nombre_duplicado() {
        when(motivoCancelacionRepository.existsByNombreIgnoreCase("Enfermedad")).thenReturn(true);
        assertStatus(() -> motivoCancelacionService.crear(new MotivoCancelacionRequest("Enfermedad", null)), HttpStatus.CONFLICT);
    }

    @Test
    void actualizar_modifica_los_datos() {
        MotivoCancelacion existente = MotivoCancelacion.builder().id(1L).nombre("A").build();
        when(motivoCancelacionRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(motivoCancelacionRepository.existsByNombreIgnoreCaseAndIdNot("B", 1L)).thenReturn(false);
        when(motivoCancelacionRepository.save(any(MotivoCancelacion.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(motivoCancelacionService.actualizar(1L, new MotivoCancelacionRequest("B", "desc")).nombre()).isEqualTo("B");
    }

    @Test
    void actualizar_rechaza_el_nombre_de_otro_motivo() {
        when(motivoCancelacionRepository.findById(1L)).thenReturn(Optional.of(MotivoCancelacion.builder().id(1L).nombre("A").build()));
        when(motivoCancelacionRepository.existsByNombreIgnoreCaseAndIdNot("B", 1L)).thenReturn(true);

        assertStatus(() -> motivoCancelacionService.actualizar(1L, new MotivoCancelacionRequest("B", null)), HttpStatus.CONFLICT);
    }

    @Test
    void eliminar_borra_el_motivo() {
        MotivoCancelacion existente = MotivoCancelacion.builder().id(1L).nombre("A").build();
        when(motivoCancelacionRepository.findById(1L)).thenReturn(Optional.of(existente));

        motivoCancelacionService.eliminar(1L);

        verify(motivoCancelacionRepository).delete(existente);
    }

    @Test
    void eliminar_rechaza_uno_inexistente() {
        when(motivoCancelacionRepository.findById(1L)).thenReturn(Optional.empty());
        assertStatus(() -> motivoCancelacionService.eliminar(1L), HttpStatus.NOT_FOUND);
    }
}
