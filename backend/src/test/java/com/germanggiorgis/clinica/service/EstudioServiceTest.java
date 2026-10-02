package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.EstudioRequest;
import com.germanggiorgis.clinica.dto.EstudioResponse;
import com.germanggiorgis.clinica.model.Estudio;
import com.germanggiorgis.clinica.repository.EstudioRepository;
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
class EstudioServiceTest {

    @Mock
    private EstudioRepository estudioRepository;

    @InjectMocks
    private EstudioService estudioService;

    @Test
    void listar_devuelve_todos() {
        when(estudioRepository.findAll()).thenReturn(List.of(Estudio.builder().id(1L).nombre("Hemograma").tipo("Laboratorio").build()));
        assertThat(estudioService.listar()).hasSize(1);
    }

    @Test
    void crear_guarda_un_estudio_nuevo() {
        when(estudioRepository.existsByNombreIgnoreCase("Hemograma")).thenReturn(false);
        when(estudioRepository.save(any(Estudio.class))).thenAnswer(inv -> {
            Estudio e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });

        EstudioResponse respuesta = estudioService.crear(new EstudioRequest("Hemograma", "Laboratorio"));

        assertThat(respuesta.id()).isEqualTo(1L);
        assertThat(respuesta.tipo()).isEqualTo("Laboratorio");
    }

    @Test
    void crear_rechaza_un_nombre_duplicado() {
        when(estudioRepository.existsByNombreIgnoreCase("Hemograma")).thenReturn(true);
        assertStatus(() -> estudioService.crear(new EstudioRequest("Hemograma", "Laboratorio")), HttpStatus.CONFLICT);
    }

    @Test
    void actualizar_modifica_los_datos() {
        Estudio existente = Estudio.builder().id(1L).nombre("A").tipo("Lab").build();
        when(estudioRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(estudioRepository.existsByNombreIgnoreCaseAndIdNot("B", 1L)).thenReturn(false);
        when(estudioRepository.save(any(Estudio.class))).thenAnswer(inv -> inv.getArgument(0));

        EstudioResponse respuesta = estudioService.actualizar(1L, new EstudioRequest("B", "Imagen"));

        assertThat(respuesta.nombre()).isEqualTo("B");
        assertThat(respuesta.tipo()).isEqualTo("Imagen");
    }

    @Test
    void actualizar_rechaza_el_nombre_de_otro_estudio() {
        when(estudioRepository.findById(1L)).thenReturn(Optional.of(Estudio.builder().id(1L).nombre("A").tipo("Lab").build()));
        when(estudioRepository.existsByNombreIgnoreCaseAndIdNot("B", 1L)).thenReturn(true);

        assertStatus(() -> estudioService.actualizar(1L, new EstudioRequest("B", "Lab")), HttpStatus.CONFLICT);
    }

    @Test
    void eliminar_borra_el_estudio() {
        Estudio existente = Estudio.builder().id(1L).nombre("A").tipo("Lab").build();
        when(estudioRepository.findById(1L)).thenReturn(Optional.of(existente));

        estudioService.eliminar(1L);

        verify(estudioRepository).delete(existente);
    }

    @Test
    void eliminar_rechaza_uno_inexistente() {
        when(estudioRepository.findById(1L)).thenReturn(Optional.empty());
        assertStatus(() -> estudioService.eliminar(1L), HttpStatus.NOT_FOUND);
    }
}
