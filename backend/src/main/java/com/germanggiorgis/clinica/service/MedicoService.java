package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.MedicoResponse;
import com.germanggiorgis.clinica.model.Medico;
import com.germanggiorgis.clinica.repository.MedicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Público: un paciente navega médicos antes incluso de loguearse. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MedicoService {

    private final MedicoRepository medicoRepository;

    public List<MedicoResponse> listar() {
        return medicoRepository.findAll().stream().map(MedicoResponse::from).toList();
    }

    public MedicoResponse obtener(Long id) {
        return MedicoResponse.from(medicoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Médico no encontrado")));
    }

    Medico obtenerEntidad(Long id) {
        return medicoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Médico no encontrado"));
    }
}
