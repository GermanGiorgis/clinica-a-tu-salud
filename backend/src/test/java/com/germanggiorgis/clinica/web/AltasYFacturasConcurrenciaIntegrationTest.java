package com.germanggiorgis.clinica.web;

import com.germanggiorgis.clinica.model.Consulta;
import com.germanggiorgis.clinica.model.Consultorio;
import com.germanggiorgis.clinica.model.EstadoTurno;
import com.germanggiorgis.clinica.model.Medico;
import com.germanggiorgis.clinica.model.Paciente;
import com.germanggiorgis.clinica.model.Turno;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Dos pedidos idénticos a la vez (el doble clic, o dos pestañas). Antes de manejar las violaciones de
 * restricciones, el que perdía la carrera contra el índice único terminaba en un 500.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/clinica_test",
        "jwt.secret=secreto-solo-para-tests-no-es-un-secreto-real-0123456789abcdef"
})
class AltasYFacturasConcurrenciaIntegrationTest extends BaseIntegracionConDatos {

    private static final int RONDAS = 6;

    @Test
    void dos_registros_simultaneos_con_el_mismo_email_uno_se_crea_y_el_otro_recibe_409() throws Exception {
        for (int ronda = 0; ronda < RONDAS; ronda++) {
            String email = "doble-" + UUID.randomUUID().toString().substring(0, 8) + DOMINIO;
            // Cada pedido desde su IP, para no tropezar con el límite de altas por IP.
            int[] resultado = enParalelo(
                    alta(email, "3000" + ronda + "1", "10.70." + ronda + ".1"),
                    alta(email.toUpperCase().replace(DOMINIO.toUpperCase(), DOMINIO), "3000" + ronda + "2", "10.70." + ronda + ".2"));

            assertThat(ordenados(resultado)).as("ronda %d", ronda).containsExactly(201, 409);
        }
    }

    @Test
    void el_doble_clic_en_facturar_crea_una_sola_factura_y_el_segundo_pedido_recibe_409() throws Exception {
        Medico medico = medico();
        Consultorio consultorio = consultorio();
        Paciente paciente = paciente();

        for (int ronda = 0; ronda < RONDAS; ronda++) {
            LocalDateTime inicio = LocalDateTime.now().minusDays(3).withHour(8).withMinute(0).withSecond(0).withNano(0).plusMinutes(30L * ronda);
            Turno turno = turnoRepository.saveAndFlush(Turno.builder().medico(medico).paciente(paciente).consultorio(consultorio)
                    .creadoPor(paciente.getUsuario()).inicio(inicio).fin(inicio.plusMinutes(30)).estado(EstadoTurno.ATENDIDO).build());
            consultaRepository.saveAndFlush(Consulta.builder().turno(turno).notas("n").build());

            int[] resultado = enParalelo(facturar(turno, medico), facturar(turno, medico));

            assertThat(ordenados(resultado)).as("ronda %d", ronda).containsExactly(201, 409);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM factura WHERE turno_id = ?", Integer.class, turno.getId())).isEqualTo(1);
        }
    }

    private MockHttpServletRequestBuilder alta(String email, String dni, String ip) {
        return post("/api/auth/registro").with(desde(ip)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Nombre\",\"apellido\":\"Apellido\",\"email\":\"" + email + "\",\"password\":\"clave-de-prueba-1234\","
                        + "\"dni\":\"" + dni + "\",\"fechaNacimiento\":\"1990-01-01\"}");
    }

    private MockHttpServletRequestBuilder facturar(Turno turno, Medico medico) {
        return post("/api/turnos/" + turno.getId() + "/factura").header("Authorization", token(medico.getUsuario()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"medioPago\":\"EFECTIVO\",\"items\":[{\"descripcion\":\"Consulta\",\"importe\":100}]}");
    }
}
