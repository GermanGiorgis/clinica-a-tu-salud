package com.germanggiorgis.clinica.web;

import com.germanggiorgis.clinica.model.Consultorio;
import com.germanggiorgis.clinica.model.Especialidad;
import com.germanggiorgis.clinica.model.EstadoTurno;
import com.germanggiorgis.clinica.model.Medico;
import com.germanggiorgis.clinica.model.Paciente;
import com.germanggiorgis.clinica.model.Turno;
import com.germanggiorgis.clinica.repository.EspecialidadRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El problema N+1: una lista de N filas que dispara una consulta extra por fila. Se mide con las
 * estadísticas de Hibernate: la cantidad de sentencias SQL de un listado tiene que ser la misma con
 * pocas filas que con muchas. (Antes: 15 turnos = 50 consultas; 20 médicos = 41.)
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/clinica_test",
        "jwt.secret=secreto-solo-para-tests-no-es-un-secreto-real-0123456789abcdef",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
class ConsultasSinNMasUnoIntegrationTest extends BaseIntegracionConDatos {

    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private EspecialidadRepository especialidadRepository;

    private long sentenciasDe(MockHttpServletRequestBuilder request) throws Exception {
        Statistics estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estadisticas.setStatisticsEnabled(true);
        estadisticas.clear();
        mockMvc.perform(request).andExpect(status().isOk());
        return estadisticas.getPrepareStatementCount();
    }

    /** Turnos con médicos y consultorios todos distintos: el peor caso para el N+1. */
    private void agregarTurnosDelPaciente(Paciente paciente, int cantidad) {
        for (int i = 0; i < cantidad; i++) {
            LocalDateTime inicio = LocalDateTime.now().plusDays(5).withHour(8).withMinute(0).withSecond(0).withNano(0).plusMinutes(30L * i);
            turnoRepository.saveAndFlush(Turno.builder().medico(medico()).paciente(paciente).consultorio(consultorio())
                    .creadoPor(paciente.getUsuario()).inicio(inicio).fin(inicio.plusMinutes(30)).estado(EstadoTurno.RESERVADO).build());
        }
    }

    /** Turnos de un mismo médico con pacientes todos distintos, uno a continuación del otro. */
    private void agregarTurnosDelMedico(Medico medico, int cantidad) {
        Consultorio consultorio = consultorio();
        for (int i = 0; i < cantidad; i++) {
            Paciente paciente = paciente();
            LocalDateTime inicio = LocalDateTime.now().plusDays(5).withHour(8).withMinute(0).withSecond(0).withNano(0).plusMinutes(30L * i);
            turnoRepository.saveAndFlush(Turno.builder().medico(medico).paciente(paciente).consultorio(consultorio)
                    .creadoPor(paciente.getUsuario()).inicio(inicio).fin(inicio.plusMinutes(30)).estado(EstadoTurno.RESERVADO).build());
        }
    }

    @Test
    void mis_turnos_hace_las_mismas_consultas_con_2_turnos_que_con_15() throws Exception {
        Paciente conPocos = paciente();
        Paciente conMuchos = paciente();
        agregarTurnosDelPaciente(conPocos, 2);
        agregarTurnosDelPaciente(conMuchos, 15);

        long pocos = sentenciasDe(get("/api/turnos/mios").header("Authorization", token(conPocos.getUsuario())));
        long muchos = sentenciasDe(get("/api/turnos/mios").header("Authorization", token(conMuchos.getUsuario())));

        assertThat(muchos).as("consultas con 15 turnos vs con 2").isEqualTo(pocos);
        assertThat(muchos).isLessThanOrEqualTo(6);
    }

    @Test
    void la_agenda_del_medico_hace_las_mismas_consultas_con_2_turnos_que_con_15() throws Exception {
        Medico conPocos = medico();
        Medico conMuchos = medico();
        agregarTurnosDelMedico(conPocos, 2);
        agregarTurnosDelMedico(conMuchos, 15);

        long pocos = sentenciasDe(get("/api/turnos/agenda").header("Authorization", token(conPocos.getUsuario())));
        long muchos = sentenciasDe(get("/api/turnos/agenda").header("Authorization", token(conMuchos.getUsuario())));

        assertThat(muchos).as("consultas con 15 turnos vs con 2").isEqualTo(pocos);
        assertThat(muchos).isLessThanOrEqualTo(6);
    }

    @Test
    void la_lista_de_medicos_hace_las_mismas_consultas_con_3_medicos_que_con_15_mas() throws Exception {
        for (int i = 0; i < 3; i++) medicoConEspecialidad();
        long pocos = sentenciasDe(get("/api/medicos"));

        for (int i = 0; i < 15; i++) medicoConEspecialidad();
        long muchos = sentenciasDe(get("/api/medicos"));

        assertThat(muchos).as("consultas con 15 médicos más vs sin ellos").isEqualTo(pocos);
        assertThat(muchos).isLessThanOrEqualTo(4);
    }

    @Test
    void filtrar_medicos_por_especialidad_tampoco_hace_una_consulta_por_medico() throws Exception {
        Especialidad especialidad = especialidadRepository.saveAndFlush(Especialidad.builder().nombre(PREFIJO + UUID.randomUUID()).build());
        for (int i = 0; i < 3; i++) medicoDeLaEspecialidad(especialidad);
        long pocos = sentenciasDe(get("/api/medicos").param("especialidadId", String.valueOf(especialidad.getId())));

        for (int i = 0; i < 12; i++) medicoDeLaEspecialidad(especialidad);
        long muchos = sentenciasDe(get("/api/medicos").param("especialidadId", String.valueOf(especialidad.getId())));

        assertThat(muchos).isEqualTo(pocos);
        assertThat(muchos).isLessThanOrEqualTo(4);
    }

    private void medicoConEspecialidad() {
        medicoDeLaEspecialidad(especialidadRepository.saveAndFlush(Especialidad.builder().nombre(PREFIJO + UUID.randomUUID()).build()));
    }

    private void medicoDeLaEspecialidad(Especialidad especialidad) {
        Medico medico = medico();
        medico.setEspecialidades(List.of(especialidad));
        medicoRepository.saveAndFlush(medico);
    }
}
