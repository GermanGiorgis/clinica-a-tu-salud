package com.germanggiorgis.clinica.config;

import com.germanggiorgis.clinica.model.Especialidad;
import com.germanggiorgis.clinica.model.HorarioAtencion;
import com.germanggiorgis.clinica.model.Medico;
import com.germanggiorgis.clinica.repository.EspecialidadRepository;
import com.germanggiorgis.clinica.repository.HorarioAtencionRepository;
import com.germanggiorgis.clinica.repository.MedicoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los profesionales de demostración que se cargan en desarrollo local. Cada prueba corre en una transacción que se
 * deshace, así que no queda nada en la base de pruebas (en la que el cargador está apagado, como en producción).
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/clinica_test",
        "jwt.secret=secreto-solo-para-tests-no-es-un-secreto-real-0123456789abcdef"
})
class MedicosDeDemostracionIntegrationTest {

    @Autowired private MedicosDeDemostracion cargador;
    @Autowired private MedicoRepository medicoRepository;
    @Autowired private EspecialidadRepository especialidadRepository;
    @Autowired private HorarioAtencionRepository horarioRepository;

    private void cargar() {
        ReflectionTestUtils.setField(cargador, "activado", true);
        cargador.run(null);
    }

    private List<Medico> deDemostracion() {
        return medicoRepository.findAll().stream()
                .filter(m -> m.getUsuario().getEmail().endsWith(MedicosDeDemostracion.DOMINIO)).toList();
    }

    @Test
    void apagado_no_carga_nada() {
        ReflectionTestUtils.setField(cargador, "activado", false);

        cargador.run(null);

        assertThat(deDemostracion()).isEmpty();
    }

    @Test
    void cada_especialidad_del_catalogo_queda_con_al_menos_dos_profesionales() {
        cargar();

        Map<Long, Long> porEspecialidad = deDemostracion().stream().flatMap(m -> m.getEspecialidades().stream())
                .collect(Collectors.groupingBy(Especialidad::getId, Collectors.counting()));
        for (Especialidad e : especialidadRepository.findAll()) {
            assertThat(porEspecialidad.getOrDefault(e.getId(), 0L)).as(e.getNombre()).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    void algunos_atienden_mas_de_una_especialidad() {
        cargar();

        Medico ginecologo = deDemostracion().stream()
                .filter(m -> m.getEspecialidades().stream().anyMatch(e -> e.getNombre().equals("Ginecología")))
                .filter(m -> m.getEspecialidades().size() > 1).findFirst().orElseThrow();

        assertThat(ginecologo.getEspecialidades()).extracting(Especialidad::getNombre).contains("Ginecología", "Obstetricia");
    }

    @Test
    void cada_profesional_atiende_tres_dias_y_los_de_una_misma_especialidad_no_se_pisan_en_el_consultorio() {
        cargar();

        for (Medico medico : deDemostracion()) {
            assertThat(horarioRepository.findByMedicoId(medico.getId())).hasSize(3);
        }
        List<HorarioAtencion> todos = deDemostracion().stream().flatMap(m -> horarioRepository.findByMedicoId(m.getId()).stream()).toList();
        for (HorarioAtencion a : todos) {
            for (HorarioAtencion b : todos) {
                if (a != b && a.getConsultorio().getId().equals(b.getConsultorio().getId()) && a.getDiaSemana() == b.getDiaSemana()) {
                    boolean seSuperponen = a.getHoraInicio().isBefore(b.getHoraFin()) && b.getHoraInicio().isBefore(a.getHoraFin());
                    assertThat(seSuperponen).as("%s %s", a.getConsultorio().getNombre(), a.getDiaSemana()).isFalse();
                }
            }
        }
    }

    @Test
    void cargar_dos_veces_no_repite_a_nadie() {
        cargar();
        int primera = deDemostracion().size();

        cargar();

        assertThat(deDemostracion()).hasSize(primera);
        assertThat(primera).isGreaterThanOrEqualTo(60);
    }

    @Test
    void una_especialidad_agregada_despues_recibe_sus_profesionales_sin_chocar_con_los_anteriores() {
        cargar();
        int antes = deDemostracion().size();
        Especialidad nueva = especialidadRepository.saveAndFlush(Especialidad.builder().nombre("Aaa Demostración nueva").build());

        cargar();

        assertThat(deDemostracion()).hasSize(antes + 2);
        assertThat(deDemostracion().stream().filter(m -> m.getEspecialidades().contains(nueva))).hasSize(2);
        assertThat(deDemostracion()).extracting(m -> m.getUsuario().getEmail()).doesNotHaveDuplicates();
        assertThat(deDemostracion()).extracting(Medico::getMatricula).doesNotHaveDuplicates();
    }

    @Test
    void las_cuentas_quedan_activas_con_rol_medico_y_sin_una_contrasena_conocida() {
        cargar();

        for (Medico medico : deDemostracion()) {
            assertThat(medico.getUsuario().isActivo()).isTrue();
            assertThat(medico.getUsuario().getRol().name()).isEqualTo("MEDICO");
            assertThat(medico.getUsuario().getPasswordHash()).startsWith("$2");
        }
    }
}
