package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.HorarioAtencionRequest;
import com.germanggiorgis.clinica.dto.HorarioAtencionResponse;
import com.germanggiorgis.clinica.model.Consultorio;
import com.germanggiorgis.clinica.model.HorarioAtencion;
import com.germanggiorgis.clinica.model.Medico;
import com.germanggiorgis.clinica.repository.ConsultorioRepository;
import com.germanggiorgis.clinica.repository.HorarioAtencionRepository;
import com.germanggiorgis.clinica.repository.MedicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;
import java.util.List;

/**
 * Regla 6 de docs/modelo-datos.md: los horarios de un mismo médico no se
 * pisan entre sí, y un consultorio no puede tener dos médicos en el mismo
 * horario. Acá se valida en Java (con mensajes claros); los CHECK de la
 * migración (horaFin > horaInicio, duración, vigencia) son el respaldo a
 * nivel de base de datos.
 *
 * {@code @Transactional}: con open-in-view=false (deliberado, ver
 * application.properties) la sesión de Hibernate se cierra al terminar cada
 * llamada al repositorio. Como HorarioAtencionResponse.from navega
 * relaciones LAZY (horario -> medico -> usuario), sin esto cada método
 * tiraría LazyInitializationException al armar la respuesta.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class HorarioAtencionService {

    private final HorarioAtencionRepository horarioAtencionRepository;
    private final MedicoRepository medicoRepository;
    private final ConsultorioRepository consultorioRepository;

    public List<HorarioAtencionResponse> listar(Long medicoId) {
        List<HorarioAtencion> horarios = medicoId != null
                ? horarioAtencionRepository.findByMedicoId(medicoId)
                : horarioAtencionRepository.findAll();
        return horarios.stream().map(HorarioAtencionResponse::from).toList();
    }

    public HorarioAtencionResponse crear(HorarioAtencionRequest request) {
        Medico medico = obtenerMedico(request.medicoId());
        Consultorio consultorio = obtenerConsultorio(request.consultorioId());
        validar(request, null);

        HorarioAtencion horario = HorarioAtencion.builder()
                .medico(medico)
                .consultorio(consultorio)
                .diaSemana(request.diaSemana())
                .horaInicio(request.horaInicio())
                .horaFin(request.horaFin())
                .duracionTurnoMin(request.duracionTurnoMin())
                .vigenteDesde(request.vigenteDesde())
                .vigenteHasta(request.vigenteHasta())
                .build();

        return HorarioAtencionResponse.from(horarioAtencionRepository.save(horario));
    }

    public HorarioAtencionResponse actualizar(Long id, HorarioAtencionRequest request) {
        HorarioAtencion horario = obtener(id);
        Medico medico = obtenerMedico(request.medicoId());
        Consultorio consultorio = obtenerConsultorio(request.consultorioId());
        validar(request, id);

        horario.setMedico(medico);
        horario.setConsultorio(consultorio);
        horario.setDiaSemana(request.diaSemana());
        horario.setHoraInicio(request.horaInicio());
        horario.setHoraFin(request.horaFin());
        horario.setDuracionTurnoMin(request.duracionTurnoMin());
        horario.setVigenteDesde(request.vigenteDesde());
        horario.setVigenteHasta(request.vigenteHasta());

        return HorarioAtencionResponse.from(horarioAtencionRepository.save(horario));
    }

    public void eliminar(Long id) {
        horarioAtencionRepository.delete(obtener(id));
    }

    private void validar(HorarioAtencionRequest request, Long idAIgnorar) {
        if (!request.horaFin().isAfter(request.horaInicio())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La hora de fin debe ser posterior a la de inicio");
        }
        if (request.vigenteHasta() != null && request.vigenteHasta().isBefore(request.vigenteDesde())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La vigencia hasta no puede ser anterior a la de desde");
        }

        boolean medicoOcupado = horarioAtencionRepository
                .findByMedicoIdAndDiaSemana(request.medicoId(), request.diaSemana()).stream()
                .filter(h -> !h.getId().equals(idAIgnorar))
                .anyMatch(h -> seSuperponen(request.horaInicio(), request.horaFin(), h.getHoraInicio(), h.getHoraFin()));
        if (medicoOcupado) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El médico ya tiene un horario que se superpone ese día");
        }

        boolean consultorioOcupado = horarioAtencionRepository
                .findByConsultorioIdAndDiaSemana(request.consultorioId(), request.diaSemana()).stream()
                .filter(h -> !h.getId().equals(idAIgnorar))
                .anyMatch(h -> seSuperponen(request.horaInicio(), request.horaFin(), h.getHoraInicio(), h.getHoraFin()));
        if (consultorioOcupado) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El consultorio ya está ocupado por otro médico en ese horario");
        }
    }

    private boolean seSuperponen(LocalTime inicioA, LocalTime finA, LocalTime inicioB, LocalTime finB) {
        return inicioA.isBefore(finB) && inicioB.isBefore(finA);
    }

    private HorarioAtencion obtener(Long id) {
        return horarioAtencionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Horario no encontrado"));
    }

    private Medico obtenerMedico(Long id) {
        return medicoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Médico no encontrado"));
    }

    private Consultorio obtenerConsultorio(Long id) {
        return consultorioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consultorio no encontrado"));
    }
}
