package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.CancelarTurnoRequest;
import com.germanggiorgis.clinica.dto.CrearDiaNoLaborableRequest;
import com.germanggiorgis.clinica.dto.DiaNoLaborableResponse;
import com.germanggiorgis.clinica.dto.TurnoAfectadoResponse;
import com.germanggiorgis.clinica.model.DiaNoLaborable;
import com.germanggiorgis.clinica.model.EstadoTurno;
import com.germanggiorgis.clinica.model.Medico;
import com.germanggiorgis.clinica.model.MotivoCancelacion;
import com.germanggiorgis.clinica.model.Turno;
import com.germanggiorgis.clinica.repository.DiaNoLaborableRepository;
import com.germanggiorgis.clinica.repository.MedicoRepository;
import com.germanggiorgis.clinica.repository.MotivoCancelacionRepository;
import com.germanggiorgis.clinica.repository.TurnoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;

/**
 * Feriados, cierres y ausencias de médicos, cargados por anticipado (regla 18 de
 * docs/modelo-datos.md). Solo ADMIN y RECEPCIONISTA llegan acá (lo exige SecurityConfig).
 *
 * <p>Cargar un cierre NO cancela nada por sí solo: si ya había turnos en ese período se listan
 * ({@link #turnosAfectados}) y quien está en el mostrador decide si los cancela de una vez
 * ({@link #cancelarTurnosAfectados}) después de avisarles a los pacientes. Un cancelado en bloque
 * es una decisión con consecuencias; no se dispara como efecto lateral de una carga.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DiaNoLaborableService {

    /** Un rango más largo que esto es casi seguro un error de carga (ver también el CHECK de V9). */
    static final int MAXIMO_DIAS = 365;
    private static final Collection<EstadoTurno> PENDIENTES = List.of(EstadoTurno.RESERVADO, EstadoTurno.CONFIRMADO);

    private final DiaNoLaborableRepository diaNoLaborableRepository;
    private final MedicoRepository medicoRepository;
    private final TurnoRepository turnoRepository;
    private final MotivoCancelacionRepository motivoCancelacionRepository;
    private final UsuarioContextService usuarioContextService;
    private final Clock clock;

    /** Los cierres que todavía no terminaron: lo único que hace falta para operar. */
    @Transactional(readOnly = true)
    public List<DiaNoLaborableResponse> listar() {
        return diaNoLaborableRepository.findByHastaGreaterThanEqualOrderByDesdeAscIdAsc(LocalDate.now(clock)).stream()
                .map(DiaNoLaborableResponse::from)
                .toList();
    }

    public DiaNoLaborableResponse crear(CrearDiaNoLaborableRequest request) {
        if (request.hasta().isBefore(request.desde())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha final no puede ser anterior a la inicial");
        }
        if (request.hasta().isBefore(LocalDate.now(clock))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ese período ya terminó");
        }
        if (ChronoUnit.DAYS.between(request.desde(), request.hasta()) > MAXIMO_DIAS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Un período no puede durar más de " + MAXIMO_DIAS + " días");
        }
        Medico medico = request.medicoId() == null ? null : medicoRepository.findById(request.medicoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Médico no encontrado"));

        DiaNoLaborable dia = diaNoLaborableRepository.save(DiaNoLaborable.builder()
                .desde(request.desde())
                .hasta(request.hasta())
                .descripcion(request.descripcion().strip())
                .medico(medico)
                .creadoPor(usuarioContextService.getUsuarioActual())
                .creadoEn(LocalDateTime.now(clock))
                .build());
        return DiaNoLaborableResponse.from(dia);
    }

    public void eliminar(Long id) {
        diaNoLaborableRepository.delete(obtener(id));
    }

    /** Turnos pendientes que todavía no empezaron y caen dentro del período. */
    @Transactional(readOnly = true)
    public List<TurnoAfectadoResponse> turnosAfectados(Long id) {
        return afectados(obtener(id)).stream().map(TurnoAfectadoResponse::from).toList();
    }

    /** Cancela todos los turnos afectados con el mismo motivo y devuelve cuántos fueron. */
    public int cancelarTurnosAfectados(Long id, CancelarTurnoRequest request) {
        DiaNoLaborable dia = obtener(id);
        MotivoCancelacion motivo = motivoCancelacionRepository.findById(request.motivoCancelacionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Motivo de cancelación no encontrado"));

        List<Turno> turnos = afectados(dia);
        for (Turno turno : turnos) {
            turno.setEstado(EstadoTurno.CANCELADO);
            turno.setMotivoCancelacion(motivo);
        }
        turnoRepository.saveAll(turnos);
        return turnos.size();
    }

    private List<Turno> afectados(DiaNoLaborable dia) {
        // Un turno que ya empezó no se toca: o se atendió, o es de la agenda de hoy que se cierra a mano.
        LocalDateTime desde = dia.getDesde().atStartOfDay();
        LocalDateTime ahora = LocalDateTime.now(clock);
        if (desde.isBefore(ahora)) {
            desde = ahora;
        }
        LocalDateTime hasta = dia.getHasta().plusDays(1).atStartOfDay();
        return dia.getMedico() == null
                ? turnoRepository.findByEstadoInAndInicioGreaterThanEqualAndInicioLessThanOrderByInicioAsc(
                        PENDIENTES, desde, hasta)
                : turnoRepository.findByMedicoIdAndEstadoInAndInicioGreaterThanEqualAndInicioLessThanOrderByInicioAsc(
                        dia.getMedico().getId(), PENDIENTES, desde, hasta);
    }

    private DiaNoLaborable obtener(Long id) {
        return diaNoLaborableRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Período no encontrado"));
    }
}
