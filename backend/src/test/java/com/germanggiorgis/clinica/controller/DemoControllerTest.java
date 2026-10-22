package com.germanggiorgis.clinica.controller;

import com.germanggiorgis.clinica.config.ReinicioDeDemostracion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DemoControllerTest {

    @Mock private ReinicioDeDemostracion reinicio;
    private MockMvc mockMvc;

    @BeforeEach
    void montar() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DemoController(reinicio)).build();
    }

    @Test
    void apagado_responde_404_como_si_la_ruta_no_existiera_y_no_toca_nada() throws Exception {
        when(reinicio.estaHabilitado()).thenReturn(false);

        mockMvc.perform(get("/api/demo/reiniciar").header("Authorization", "Bearer lo-que-sea"))
                .andExpect(status().isNotFound());

        verify(reinicio, never()).reiniciar();
    }

    @Test
    void sin_encabezado_o_con_un_token_incorrecto_responde_401_y_no_borra() throws Exception {
        when(reinicio.estaHabilitado()).thenReturn(true);
        when(reinicio.tokenValido(null)).thenReturn(false);
        when(reinicio.tokenValido("Bearer mal")).thenReturn(false);

        mockMvc.perform(get("/api/demo/reiniciar")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/demo/reiniciar").header("Authorization", "Bearer mal")).andExpect(status().isUnauthorized());

        verify(reinicio, never()).reiniciar();
    }

    @Test
    void con_el_token_correcto_reinicia_una_vez_y_lo_informa() throws Exception {
        when(reinicio.estaHabilitado()).thenReturn(true);
        when(reinicio.tokenValido("Bearer bien")).thenReturn(true);

        mockMvc.perform(get("/api/demo/reiniciar").header("Authorization", "Bearer bien"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("reiniciado")));

        verify(reinicio).reiniciar();
    }
}
