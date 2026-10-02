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
import com.germanggiorgis.clinica.repository.EncuestaRepository;
import com.germanggiorgis.clinica.repository.FacturaRepository;
import com.germanggiorgis.clinica.repository.MedicoRepository;
import com.germanggiorgis.clinica.repository.TurnoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Panel de métricas (solo ADMIN, lo exige SecurityConfig). Los conteos y
 * sumas los hace la base con GROUP BY; acá solo se completan los ceros, se
 * pivotea por médico y se calculan los porcentajes.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MetricasService {

    private final TurnoRepository turnoRepository;
    private final EncuestaRepository encuestaRepository;
    private final FacturaRepository facturaRepository;
    private final MedicoRepository medicoRepository;

    public MetricasResponse obtener() {
        Map<EstadoTurno, Long> porEstado = new EnumMap<>(EstadoTurno.class);
        for (EstadoTurno estado : EstadoTurno.values()) {
            porEstado.put(estado, 0L);
        }
        turnoRepository.contarPorEstado().forEach(c -> porEstado.put(c.estado(), c.cantidad()));
        List<ConteoPorEstado> turnosPorEstado = porEstado.entrySet().stream()
                .map(e -> new ConteoPorEstado(e.getKey(), e.getValue()))
                .toList();

        long atendidos = porEstado.get(EstadoTurno.ATENDIDO);
        long ausentes = porEstado.get(EstadoTurno.AUSENTE);
        long totalTurnos = porEstado.values().stream().mapToLong(Long::longValue).sum();

        Map<MedioPago, MontoPorMedioPago> porMedioPago = new EnumMap<>(MedioPago.class);
        for (MedioPago medioPago : MedioPago.values()) {
            porMedioPago.put(medioPago, new MontoPorMedioPago(medioPago, 0, BigDecimal.ZERO));
        }
        facturaRepository.totalesPorMedioPago().forEach(m -> porMedioPago.put(m.medioPago(), m));
        List<MontoPorMedioPago> facturacionPorMedioPago = List.copyOf(porMedioPago.values());
        BigDecimal facturacionTotal = facturacionPorMedioPago.stream()
                .map(MontoPorMedioPago::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new MetricasResponse(
                totalTurnos,
                turnoRepository.contarPacientesDistintosConEstado(EstadoTurno.ATENDIDO),
                porcentaje(ausentes, atendidos + ausentes),
                redondear(encuestaRepository.puntajePromedio()),
                encuestaRepository.count(),
                facturacionTotal,
                turnosPorEstado,
                facturacionPorMedioPago,
                porMedico()
        );
    }

    private List<MetricaMedico> porMedico() {
        Map<Long, Map<EstadoTurno, Long>> turnos = new HashMap<>();
        for (ConteoMedicoEstado c : turnoRepository.contarPorMedicoYEstado()) {
            turnos.computeIfAbsent(c.medicoId(), id -> new EnumMap<>(EstadoTurno.class)).put(c.estado(), c.cantidad());
        }
        Map<Long, PuntajeMedico> puntajes = new HashMap<>();
        encuestaRepository.puntajePorMedico().forEach(p -> puntajes.put(p.medicoId(), p));
        Map<Long, BigDecimal> facturado = new HashMap<>();
        for (MontoMedico m : facturaRepository.totalesPorMedico()) {
            facturado.put(m.medicoId(), m.total());
        }

        return medicoRepository.findAll().stream()
                .map(medico -> {
                    Map<EstadoTurno, Long> deEste = turnos.getOrDefault(medico.getId(), Map.of());
                    PuntajeMedico puntaje = puntajes.get(medico.getId());
                    return new MetricaMedico(
                            medico.getId(),
                            medico.getUsuario().getNombre() + " " + medico.getUsuario().getApellido(),
                            deEste.getOrDefault(EstadoTurno.ATENDIDO, 0L),
                            deEste.getOrDefault(EstadoTurno.AUSENTE, 0L),
                            puntaje == null ? null : redondear(puntaje.promedio()),
                            puntaje == null ? 0 : puntaje.cantidad(),
                            facturado.getOrDefault(medico.getId(), BigDecimal.ZERO));
                })
                .sorted(Comparator.comparingLong(MetricaMedico::turnosAtendidos).reversed()
                        .thenComparing(MetricaMedico::nombre))
                .toList();
    }

    private static Double porcentaje(long parte, long total) {
        if (total == 0) {
            return null;
        }
        return redondear(100.0 * parte / total);
    }

    private static Double redondear(Double valor) {
        return valor == null ? null : Math.round(valor * 100.0) / 100.0;
    }
}
