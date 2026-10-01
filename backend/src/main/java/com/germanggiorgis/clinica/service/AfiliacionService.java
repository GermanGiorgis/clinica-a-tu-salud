package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.AfiliacionResponse;
import com.germanggiorgis.clinica.dto.CrearAfiliacionRequest;
import com.germanggiorgis.clinica.model.Afiliacion;
import com.germanggiorgis.clinica.model.ObraSocial;
import com.germanggiorgis.clinica.model.Paciente;
import com.germanggiorgis.clinica.model.Usuario;
import com.germanggiorgis.clinica.repository.AfiliacionRepository;
import com.germanggiorgis.clinica.repository.ObraSocialRepository;
import com.germanggiorgis.clinica.repository.PacienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** El paciente gestiona sus propias afiliaciones (regla: cada uno ve y edita solo lo suyo). */
@Service
@RequiredArgsConstructor
@Transactional
public class AfiliacionService {

    private final AfiliacionRepository afiliacionRepository;
    private final ObraSocialRepository obraSocialRepository;
    private final PacienteRepository pacienteRepository;
    private final UsuarioContextService usuarioContextService;

    @Transactional(readOnly = true)
    public List<AfiliacionResponse> misAfiliaciones() {
        Paciente paciente = pacienteDelUsuarioActual();
        return afiliacionRepository.findByPacienteIdOrderByVigenteDesdeDesc(paciente.getId()).stream()
                .map(AfiliacionResponse::from)
                .toList();
    }

    public AfiliacionResponse crear(CrearAfiliacionRequest request) {
        Paciente paciente = pacienteDelUsuarioActual();
        ObraSocial obraSocial = obraSocialRepository.findById(request.obraSocialId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Obra social no encontrada"));

        Afiliacion afiliacion = Afiliacion.builder()
                .paciente(paciente)
                .obraSocial(obraSocial)
                .numeroAfiliado(request.numeroAfiliado())
                .plan(request.plan())
                .vigenteDesde(request.vigenteDesde())
                .vigenteHasta(request.vigenteHasta())
                .build();
        return AfiliacionResponse.from(afiliacionRepository.save(afiliacion));
    }

    public void eliminar(Long id) {
        Paciente paciente = pacienteDelUsuarioActual();
        Afiliacion afiliacion = afiliacionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Afiliación no encontrada"));
        if (!afiliacion.getPaciente().getId().equals(paciente.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No podés eliminar la afiliación de otro paciente");
        }
        afiliacionRepository.delete(afiliacion);
    }

    private Paciente pacienteDelUsuarioActual() {
        Usuario actual = usuarioContextService.getUsuarioActual();
        return pacienteRepository.findByUsuarioId(actual.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Perfil de paciente no encontrado"));
    }
}
