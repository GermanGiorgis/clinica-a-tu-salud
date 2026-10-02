package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.MedicamentoRequest;
import com.germanggiorgis.clinica.dto.MedicamentoResponse;
import com.germanggiorgis.clinica.model.Medicamento;
import com.germanggiorgis.clinica.repository.MedicamentoRepository;
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
class MedicamentoServiceTest {

    @Mock
    private MedicamentoRepository medicamentoRepository;

    @InjectMocks
    private MedicamentoService medicamentoService;

    @Test
    void listar_devuelve_todos() {
        when(medicamentoRepository.findAll()).thenReturn(List.of(Medicamento.builder().id(1L).nombre("Ibuprofeno").build()));
        assertThat(medicamentoService.listar()).hasSize(1);
    }

    @Test
    void crear_guarda_un_medicamento_nuevo() {
        when(medicamentoRepository.existsByNombreIgnoreCase("Ibuprofeno")).thenReturn(false);
        when(medicamentoRepository.save(any(Medicamento.class))).thenAnswer(inv -> {
            Medicamento m = inv.getArgument(0);
            m.setId(1L);
            return m;
        });

        MedicamentoResponse respuesta = medicamentoService.crear(new MedicamentoRequest("Ibuprofeno", "400mg"));

        assertThat(respuesta.id()).isEqualTo(1L);
        assertThat(respuesta.presentacion()).isEqualTo("400mg");
    }

    @Test
    void crear_rechaza_un_nombre_duplicado() {
        when(medicamentoRepository.existsByNombreIgnoreCase("Ibuprofeno")).thenReturn(true);
        assertStatus(() -> medicamentoService.crear(new MedicamentoRequest("Ibuprofeno", null)), HttpStatus.CONFLICT);
    }

    @Test
    void actualizar_modifica_los_datos() {
        Medicamento existente = Medicamento.builder().id(1L).nombre("A").build();
        when(medicamentoRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(medicamentoRepository.existsByNombreIgnoreCaseAndIdNot("B", 1L)).thenReturn(false);
        when(medicamentoRepository.save(any(Medicamento.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(medicamentoService.actualizar(1L, new MedicamentoRequest("B", null)).nombre()).isEqualTo("B");
    }

    @Test
    void actualizar_rechaza_el_nombre_de_otro_medicamento() {
        when(medicamentoRepository.findById(1L)).thenReturn(Optional.of(Medicamento.builder().id(1L).nombre("A").build()));
        when(medicamentoRepository.existsByNombreIgnoreCaseAndIdNot("B", 1L)).thenReturn(true);

        assertStatus(() -> medicamentoService.actualizar(1L, new MedicamentoRequest("B", null)), HttpStatus.CONFLICT);
    }

    @Test
    void eliminar_borra_el_medicamento() {
        Medicamento existente = Medicamento.builder().id(1L).nombre("A").build();
        when(medicamentoRepository.findById(1L)).thenReturn(Optional.of(existente));

        medicamentoService.eliminar(1L);

        verify(medicamentoRepository).delete(existente);
    }

    @Test
    void eliminar_rechaza_uno_inexistente() {
        when(medicamentoRepository.findById(1L)).thenReturn(Optional.empty());
        assertStatus(() -> medicamentoService.eliminar(1L), HttpStatus.NOT_FOUND);
    }
}
