package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.ObraSocialRequest;
import com.germanggiorgis.clinica.dto.ObraSocialResponse;
import com.germanggiorgis.clinica.model.ObraSocial;
import com.germanggiorgis.clinica.repository.ObraSocialRepository;
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
class ObraSocialServiceTest {

    @Mock
    private ObraSocialRepository obraSocialRepository;

    @InjectMocks
    private ObraSocialService obraSocialService;

    @Test
    void listar_devuelve_todas() {
        when(obraSocialRepository.findAll()).thenReturn(List.of(ObraSocial.builder().id(1L).nombre("OSDE").build()));
        assertThat(obraSocialService.listar()).hasSize(1);
    }

    @Test
    void crear_guarda_una_obra_social_nueva() {
        when(obraSocialRepository.existsByNombreIgnoreCase("OSDE")).thenReturn(false);
        when(obraSocialRepository.save(any(ObraSocial.class))).thenAnswer(inv -> {
            ObraSocial o = inv.getArgument(0);
            o.setId(1L);
            return o;
        });

        ObraSocialResponse respuesta = obraSocialService.crear(new ObraSocialRequest("OSDE", "0800"));

        assertThat(respuesta.id()).isEqualTo(1L);
        assertThat(respuesta.telefono()).isEqualTo("0800");
    }

    @Test
    void crear_rechaza_un_nombre_duplicado() {
        when(obraSocialRepository.existsByNombreIgnoreCase("OSDE")).thenReturn(true);
        assertStatus(() -> obraSocialService.crear(new ObraSocialRequest("OSDE", null)), HttpStatus.CONFLICT);
    }

    @Test
    void actualizar_modifica_los_datos() {
        ObraSocial existente = ObraSocial.builder().id(1L).nombre("A").build();
        when(obraSocialRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(obraSocialRepository.existsByNombreIgnoreCaseAndIdNot("B", 1L)).thenReturn(false);
        when(obraSocialRepository.save(any(ObraSocial.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(obraSocialService.actualizar(1L, new ObraSocialRequest("B", null)).nombre()).isEqualTo("B");
    }

    @Test
    void actualizar_rechaza_el_nombre_de_otra_obra_social() {
        when(obraSocialRepository.findById(1L)).thenReturn(Optional.of(ObraSocial.builder().id(1L).nombre("A").build()));
        when(obraSocialRepository.existsByNombreIgnoreCaseAndIdNot("B", 1L)).thenReturn(true);

        assertStatus(() -> obraSocialService.actualizar(1L, new ObraSocialRequest("B", null)), HttpStatus.CONFLICT);
    }

    @Test
    void eliminar_borra_la_obra_social() {
        ObraSocial existente = ObraSocial.builder().id(1L).nombre("A").build();
        when(obraSocialRepository.findById(1L)).thenReturn(Optional.of(existente));

        obraSocialService.eliminar(1L);

        verify(obraSocialRepository).delete(existente);
    }

    @Test
    void eliminar_rechaza_una_inexistente() {
        when(obraSocialRepository.findById(1L)).thenReturn(Optional.empty());
        assertStatus(() -> obraSocialService.eliminar(1L), HttpStatus.NOT_FOUND);
    }
}
