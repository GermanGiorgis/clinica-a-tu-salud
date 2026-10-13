package com.germanggiorgis.clinica.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Quien se registra tiene que enterarse de QUÉ dato está mal: cada validación devuelve el campo y un
 * mensaje en castellano. Las pruebas con datos inválidos no gastan el cupo de altas por IP (la validación
 * va antes que el servicio); las altas válidas usan una IP propia.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/clinica_test",
        "jwt.secret=secreto-solo-para-tests-no-es-un-secreto-real-0123456789abcdef"
})
class RegistroValidacionIntegrationTest extends BaseIntegracionConDatos {

    private static final String IP = "10.70.0.1";

    private static String unico() {
        return "val-" + UUID.randomUUID().toString().substring(0, 8) + DOMINIO;
    }

    private static String json(String nombre, String email, String password, String telefono, String dni, String nacimiento) {
        return "{\"nombre\":" + comillas(nombre) + ",\"apellido\":\"Apellido\",\"email\":" + comillas(email)
                + ",\"password\":" + comillas(password) + (telefono == null ? "" : ",\"telefono\":" + comillas(telefono))
                + ",\"dni\":" + comillas(dni) + ",\"fechaNacimiento\":" + comillas(nacimiento) + "}";
    }

    private static String comillas(String valor) {
        return valor == null ? "null" : "\"" + valor + "\"";
    }

    private ResultActions registrar(String cuerpo, String ip) throws Exception {
        return mockMvc.perform(post("/api/auth/registro").with(desde(ip)).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private static String dniAlAzar() {
        return String.valueOf(10_000_000L + (long) (Math.random() * 8e7));
    }

    @Test
    void un_email_sin_dominio_se_rechaza_y_el_mensaje_dice_como_escribirlo() throws Exception {
        registrar(json("Ana", "germanggiorgis@gmail", "clave-larga-123", null, dniAlAzar(), "1998-05-22"), IP)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.email").value(containsString("nombre@dominio.com")));
    }

    @Test
    void un_email_sin_arroba_o_con_espacios_se_rechaza() throws Exception {
        for (String malo : new String[] {"germanggiorgis.gmail.com", "ger man@gmail.com", "@gmail.com", "ana@.com", "ana@gmail."}) {
            registrar(json("Ana", malo, "clave-larga-123", null, dniAlAzar(), "1998-05-22"), IP)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores.email").exists());
        }
    }

    @Test
    void un_telefono_con_letras_o_demasiado_corto_o_largo_se_rechaza_y_es_el_unico_campo_marcado() throws Exception {
        for (String malo : new String[] {"abc", "12345", "261-ABC-1234", "1".repeat(16), "+".repeat(10)}) {
            registrar(json("Ana", unico(), "clave-larga-123", malo, dniAlAzar(), "1998-05-22"), IP)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores.telefono").value(containsString("entre 8 y 15 números")))
                    .andExpect(jsonPath("$.errores", not(hasKey("email"))));
        }
    }

    @Test
    void los_formatos_habituales_de_telefono_de_argentina_se_aceptan() throws Exception {
        String[] buenos = {"2613429556", "+54 9 261 342-9556", "(0261) 342-9556", "0261 4291234"};
        for (int i = 0; i < buenos.length; i++) {
            registrar(json("Ana", unico(), "clave-larga-123", buenos[i], dniAlAzar(), "1998-05-22"), "10.70.1." + i)
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void el_telefono_es_opcional_pero_en_blanco_tambien_se_acepta() throws Exception {
        registrar(json("Ana", unico(), "clave-larga-123", null, dniAlAzar(), "1998-05-22"), "10.70.2.1").andExpect(status().isCreated());
        registrar(json("Ana", unico(), "clave-larga-123", "", dniAlAzar(), "1998-05-22"), "10.70.2.2").andExpect(status().isCreated());
    }

    @Test
    void una_fecha_de_nacimiento_futura_o_anterior_a_1900_se_rechaza_con_su_propio_mensaje() throws Exception {
        registrar(json("Ana", unico(), "clave-larga-123", null, dniAlAzar(), "2999-01-01"), IP)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.fechaNacimiento").value(containsString("anterior a hoy")));
        registrar(json("Ana", unico(), "clave-larga-123", null, dniAlAzar(), "0198-05-22"), IP)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.fechaNacimiento").value(containsString("1900")));
    }

    @Test
    void los_datos_obligatorios_vacios_dicen_es_obligatorio_y_no_jerga_de_programador() throws Exception {
        registrar(json("", unico(), "clave-larga-123", null, "", null), IP)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nombre").value("es obligatorio"))
                .andExpect(jsonPath("$.errores.dni").value("es obligatorio"))
                .andExpect(jsonPath("$.errores.fechaNacimiento").value("es obligatorio"));
    }

    @Test
    void varios_datos_mal_a_la_vez_se_informan_todos_juntos_en_una_sola_respuesta() throws Exception {
        registrar(json("", "mal", "corta", "abc", "12a", "2999-01-01"), IP)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nombre").exists())
                .andExpect(jsonPath("$.errores.email").exists())
                .andExpect(jsonPath("$.errores.password").exists())
                .andExpect(jsonPath("$.errores.telefono").exists())
                .andExpect(jsonPath("$.errores.dni").exists())
                .andExpect(jsonPath("$.errores.fechaNacimiento").exists());
    }

    @Test
    void un_nombre_larguisimo_dice_cuanto_se_permite() throws Exception {
        registrar(json("a".repeat(101), unico(), "clave-larga-123", null, dniAlAzar(), "1998-05-22"), IP)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nombre").value(containsString("100 caracteres")));
    }

    @Test
    void un_email_repetido_se_informa_con_409_y_un_mensaje_claro() throws Exception {
        String email = unico();
        registrar(json("Ana", email, "clave-larga-123", null, dniAlAzar(), "1998-05-22"), "10.70.3.1").andExpect(status().isCreated());
        registrar(json("Ana", email.toUpperCase(), "clave-larga-123", null, dniAlAzar(), "1998-05-22"), "10.70.3.2")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Ya existe una cuenta con ese email"));
    }

    @Test
    void una_fecha_con_formato_raro_se_informa_como_cuerpo_invalido_sin_romper() throws Exception {
        registrar(json("Ana", unico(), "clave-larga-123", null, dniAlAzar(), "22/05/1998"), IP)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }
}
