package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.ConsultorioRequest;
import com.germanggiorgis.clinica.dto.ConsultorioResponse;
import com.germanggiorgis.clinica.model.Consultorio;
import com.germanggiorgis.clinica.repository.ConsultorioRepository;
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
class ConsultorioServiceTest {

    @Mock
    private ConsultorioRepository consultorioRepository;

    @InjectMocks
    private ConsultorioService consultorioService;

    @Test
    void listar_devuelve_todos() {
        when(consultorioRepository.findAll()).thenReturn(List.of(Consultorio.builder().id(1L).nombre("A").build()));
        assertThat(consultorioService.listar()).hasSize(1);
    }

    @Test
    void crear_guarda_un_consultorio_nuevo() {
        when(consultorioRepository.existsByNombreIgnoreCase("A")).thenReturn(false);
        when(consultorioRepository.save(any(Consultorio.class))).thenAnswer(inv -> {
            Consultorio c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });

        ConsultorioResponse respuesta = consultorioService.crear(new ConsultorioRequest("A", "Planta baja", 0));

        assertThat(respuesta.id()).isEqualTo(1L);
    }

    @Test
    void crear_rechaza_un_nombre_duplicado() {
        when(consultorioRepository.existsByNombreIgnoreCase("A")).thenReturn(true);
        assertStatus(() -> consultorioService.crear(new ConsultorioRequest("A", null, null)), HttpStatus.CONFLICT);
    }

    @Test
    void actualizar_modifica_los_datos() {
        Consultorio existente = Consultorio.builder().id(1L).nombre("A").build();
        when(consultorioRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(consultorioRepository.existsByNombreIgnoreCaseAndIdNot("B", 1L)).thenReturn(false);
        when(consultorioRepository.save(any(Consultorio.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(consultorioService.actualizar(1L, new ConsultorioRequest("B", "1er piso", 1)).nombre()).isEqualTo("B");
    }

    @Test
    void actualizar_rechaza_el_nombre_de_otro_consultorio() {
        when(consultorioRepository.findById(1L)).thenReturn(Optional.of(Consultorio.builder().id(1L).nombre("A").build()));
        when(consultorioRepository.existsByNombreIgnoreCaseAndIdNot("B", 1L)).thenReturn(true);

        assertStatus(() -> consultorioService.actualizar(1L, new ConsultorioRequest("B", null, null)), HttpStatus.CONFLICT);
    }

    @Test
    void eliminar_borra_el_consultorio() {
        Consultorio existente = Consultorio.builder().id(1L).nombre("A").build();
        when(consultorioRepository.findById(1L)).thenReturn(Optional.of(existente));

        consultorioService.eliminar(1L);

        verify(consultorioRepository).delete(existente);
    }

    @Test
    void eliminar_rechaza_uno_inexistente() {
        when(consultorioRepository.findById(1L)).thenReturn(Optional.empty());
        assertStatus(() -> consultorioService.eliminar(1L), HttpStatus.NOT_FOUND);
    }
}
