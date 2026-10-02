package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.ConteoMedicoEstado;
import com.germanggiorgis.clinica.dto.ConteoPorEstado;
import com.germanggiorgis.clinica.dto.MetricaMedico;
import com.germanggiorgis.clinica.dto.MetricasResponse;
import com.germanggiorgis.clinica.dto.MontoMedico;
import com.germanggiorgis.clinica.dto.MontoPorMedioPago;
import com.germanggiorgis.clinica.dto.PuntajeMedico;
import com.germanggiorgis.clinica.model.EstadoTurno;
import com.germanggiorgis.clinica.model.MedioPago;
import com.germanggiorgis.clinica.model.Medico;
import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Usuario;
import com.germanggiorgis.clinica.repository.EncuestaRepository;
import com.germanggiorgis.clinica.repository.FacturaRepository;
import com.germanggiorgis.clinica.repository.MedicoRepository;
import com.germanggiorgis.clinica.repository.TurnoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricasServiceTest {

    @Mock
    private TurnoRepository turnoRepository;
    @Mock
    private EncuestaRepository encuestaRepository;
    @Mock
    private FacturaRepository facturaRepository;
    @Mock
    private MedicoRepository medicoRepository;

    @InjectMocks
    private MetricasService metricasService;

    private Medico medico(long id, String nombre) {
        Usuario usuario = Usuario.builder().id(id).email("m" + id + "@clinica.test").rol(Rol.MEDICO)
                .nombre(nombre).apellido("Prueba").activo(true).build();
        return Medico.builder().id(id).usuario(usuario).matricula("MP-" + id).especialidades(List.of()).build();
    }

    private long cantidad(MetricasResponse r, EstadoTurno estado) {
        return r.turnosPorEstado().stream().filter(c -> c.estado() == estado).findFirst().orElseThrow().cantidad();
    }

    @Test
    void sin_datos_devuelve_ceros_y_nulos_en_lo_que_no_se_puede_calcular() {
        // Sin stub un mock devolvería 0.0 para Double; la base devuelve null cuando no hay encuestas.
        when(encuestaRepository.puntajePromedio()).thenReturn(null);

        MetricasResponse r = metricasService.obtener();

        assertThat(r.totalTurnos()).isZero();
        assertThat(r.pacientesAtendidos()).isZero();
        assertThat(r.tasaAusentismo()).isNull();
        assertThat(r.puntajePromedio()).isNull();
        assertThat(r.totalEncuestas()).isZero();
        assertThat(r.facturacionTotal()).isEqualByComparingTo("0");
        assertThat(r.medicos()).isEmpty();
    }

    @Test
    void siempre_informa_todos_los_estados_y_todos_los_medios_de_pago_aunque_no_tengan_datos() {
        MetricasResponse r = metricasService.obtener();

        assertThat(r.turnosPorEstado()).extracting(ConteoPorEstado::estado)
                .containsExactly(EstadoTurno.values());
        assertThat(r.turnosPorEstado()).allMatch(c -> c.cantidad() == 0);
        assertThat(r.facturacionPorMedioPago()).extracting(MontoPorMedioPago::medioPago)
                .containsExactly(MedioPago.values());
        assertThat(r.facturacionPorMedioPago()).allMatch(m -> m.cantidad() == 0 && m.total().signum() == 0);
    }

    @Test
    void suma_los_turnos_de_todos_los_estados_y_completa_con_cero_los_que_no_vinieron() {
        when(turnoRepository.contarPorEstado()).thenReturn(List.of(
                new ConteoPorEstado(EstadoTurno.ATENDIDO, 6),
                new ConteoPorEstado(EstadoTurno.CANCELADO, 3),
                new ConteoPorEstado(EstadoTurno.RESERVADO, 1)));
        when(turnoRepository.contarPacientesDistintosConEstado(EstadoTurno.ATENDIDO)).thenReturn(4L);

        MetricasResponse r = metricasService.obtener();

        assertThat(r.totalTurnos()).isEqualTo(10);
        assertThat(r.pacientesAtendidos()).isEqualTo(4);
        assertThat(cantidad(r, EstadoTurno.ATENDIDO)).isEqualTo(6);
        assertThat(cantidad(r, EstadoTurno.CANCELADO)).isEqualTo(3);
        assertThat(cantidad(r, EstadoTurno.CONFIRMADO)).isZero();
        assertThat(cantidad(r, EstadoTurno.AUSENTE)).isZero();
    }

    @Test
    void el_ausentismo_es_sobre_los_turnos_ya_resueltos_y_no_cuenta_cancelados_ni_pendientes() {
        when(turnoRepository.contarPorEstado()).thenReturn(List.of(
                new ConteoPorEstado(EstadoTurno.ATENDIDO, 7),
                new ConteoPorEstado(EstadoTurno.AUSENTE, 3),
                new ConteoPorEstado(EstadoTurno.CANCELADO, 50),
                new ConteoPorEstado(EstadoTurno.RESERVADO, 50)));

        assertThat(metricasService.obtener().tasaAusentismo()).isEqualTo(30.0);
    }

    @Test
    void el_ausentismo_se_redondea_a_dos_decimales() {
        when(turnoRepository.contarPorEstado()).thenReturn(List.of(
                new ConteoPorEstado(EstadoTurno.ATENDIDO, 2),
                new ConteoPorEstado(EstadoTurno.AUSENTE, 1)));

        assertThat(metricasService.obtener().tasaAusentismo()).isEqualTo(33.33);
    }

    @Test
    void si_todos_los_resueltos_fueron_ausentes_el_ausentismo_es_100() {
        when(turnoRepository.contarPorEstado()).thenReturn(List.of(new ConteoPorEstado(EstadoTurno.AUSENTE, 2)));

        assertThat(metricasService.obtener().tasaAusentismo()).isEqualTo(100.0);
    }

    @Test
    void el_puntaje_promedio_y_el_total_de_encuestas_salen_de_todas_las_encuestas() {
        when(encuestaRepository.puntajePromedio()).thenReturn(4.3333333);
        when(encuestaRepository.count()).thenReturn(3L);

        MetricasResponse r = metricasService.obtener();

        assertThat(r.puntajePromedio()).isEqualTo(4.33);
        assertThat(r.totalEncuestas()).isEqualTo(3);
    }

    @Test
    void la_facturacion_total_suma_todos_los_medios_de_pago() {
        when(facturaRepository.totalesPorMedioPago()).thenReturn(List.of(
                new MontoPorMedioPago(MedioPago.EFECTIVO, 1, new BigDecimal("12000.00")),
                new MontoPorMedioPago(MedioPago.OBRA_SOCIAL, 2, new BigDecimal("23500.50"))));

        MetricasResponse r = metricasService.obtener();

        assertThat(r.facturacionTotal()).isEqualByComparingTo("35500.50");
        MontoPorMedioPago efectivo = r.facturacionPorMedioPago().stream()
                .filter(m -> m.medioPago() == MedioPago.EFECTIVO).findFirst().orElseThrow();
        assertThat(efectivo.cantidad()).isEqualTo(1);
        MontoPorMedioPago tarjeta = r.facturacionPorMedioPago().stream()
                .filter(m -> m.medioPago() == MedioPago.TARJETA).findFirst().orElseThrow();
        assertThat(tarjeta.total()).isEqualByComparingTo("0");
    }

    @Test
    void arma_una_fila_por_medico_con_sus_turnos_puntaje_y_facturado() {
        when(medicoRepository.findAll()).thenReturn(List.of(medico(1, "Laura")));
        when(turnoRepository.contarPorMedicoYEstado()).thenReturn(List.of(
                new ConteoMedicoEstado(1L, EstadoTurno.ATENDIDO, 5),
                new ConteoMedicoEstado(1L, EstadoTurno.AUSENTE, 2),
                new ConteoMedicoEstado(1L, EstadoTurno.CANCELADO, 9)));
        when(encuestaRepository.puntajePorMedico()).thenReturn(List.of(new PuntajeMedico(1L, 4.5, 4)));
        when(facturaRepository.totalesPorMedico()).thenReturn(List.of(new MontoMedico(1L, new BigDecimal("700.25"))));

        List<MetricaMedico> medicos = metricasService.obtener().medicos();

        assertThat(medicos).hasSize(1);
        MetricaMedico m = medicos.get(0);
        assertThat(m.medicoId()).isEqualTo(1L);
        assertThat(m.nombre()).isEqualTo("Laura Prueba");
        assertThat(m.turnosAtendidos()).isEqualTo(5);
        assertThat(m.turnosAusentes()).isEqualTo(2);
        assertThat(m.puntajePromedio()).isEqualTo(4.5);
        assertThat(m.encuestas()).isEqualTo(4);
        assertThat(m.facturado()).isEqualByComparingTo("700.25");
    }

    @Test
    void un_medico_sin_actividad_aparece_con_ceros_y_sin_puntaje() {
        when(medicoRepository.findAll()).thenReturn(List.of(medico(7, "Nuevo")));

        MetricaMedico m = metricasService.obtener().medicos().get(0);

        assertThat(m.turnosAtendidos()).isZero();
        assertThat(m.turnosAusentes()).isZero();
        assertThat(m.puntajePromedio()).isNull();
        assertThat(m.encuestas()).isZero();
        assertThat(m.facturado()).isEqualByComparingTo("0");
    }

    @Test
    void no_mezcla_los_datos_de_un_medico_con_los_de_otro() {
        when(medicoRepository.findAll()).thenReturn(List.of(medico(1, "Ana"), medico(2, "Beto")));
        when(turnoRepository.contarPorMedicoYEstado()).thenReturn(List.of(
                new ConteoMedicoEstado(1L, EstadoTurno.ATENDIDO, 3),
                new ConteoMedicoEstado(2L, EstadoTurno.ATENDIDO, 8)));
        when(facturaRepository.totalesPorMedico()).thenReturn(List.of(new MontoMedico(2L, new BigDecimal("50.00"))));

        List<MetricaMedico> medicos = metricasService.obtener().medicos();

        MetricaMedico ana = medicos.stream().filter(m -> m.medicoId() == 1L).findFirst().orElseThrow();
        MetricaMedico beto = medicos.stream().filter(m -> m.medicoId() == 2L).findFirst().orElseThrow();
        assertThat(ana.turnosAtendidos()).isEqualTo(3);
        assertThat(ana.facturado()).isEqualByComparingTo("0");
        assertThat(beto.turnosAtendidos()).isEqualTo(8);
        assertThat(beto.facturado()).isEqualByComparingTo("50.00");
    }

    @Test
    void ordena_los_medicos_por_turnos_atendidos_y_despues_por_nombre() {
        when(medicoRepository.findAll()).thenReturn(List.of(medico(1, "Zoe"), medico(2, "Ana"), medico(3, "Beto")));
        when(turnoRepository.contarPorMedicoYEstado()).thenReturn(List.of(
                new ConteoMedicoEstado(1L, EstadoTurno.ATENDIDO, 10),
                new ConteoMedicoEstado(2L, EstadoTurno.ATENDIDO, 2),
                new ConteoMedicoEstado(3L, EstadoTurno.ATENDIDO, 2)));

        assertThat(metricasService.obtener().medicos()).extracting(MetricaMedico::medicoId)
                .containsExactly(1L, 2L, 3L);
    }
}
