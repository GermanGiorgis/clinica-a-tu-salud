package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.PacienteResponse;
import com.germanggiorgis.clinica.model.Paciente;
import com.germanggiorgis.clinica.repository.PacienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Verificación de identidad (ADMIN o RECEPCIONISTA, lo exige SecurityConfig). Quien se autorregistra queda
 * "sin verificar" hasta que el personal de la clínica compara, en persona, el DNI y los datos de la
 * cuenta con el documento. Recién entonces se puede reservar a su nombre por DNI y un médico puede
 * ver su historial. Ver V8 y AuthService.registrarPaciente.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PacienteService {

    private final PacienteRepository pacienteRepository;

    /** Cuentas de paciente activas que todavía esperan verificación. */
    @Transactional(readOnly = true)
    public List<PacienteResponse> pendientes() {
        return pacienteRepository.findByIdentidadVerificadaFalseOrderByIdAsc().stream()
                .filter(p -> p.getUsuario().isActivo())
                .map(PacienteResponse::from)
                .toList();
    }

    public PacienteResponse verificar(Long id) {
        Paciente paciente = pacienteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente no encontrado"));
        if (paciente.isIdentidadVerificada()) {
            return PacienteResponse.from(paciente);
        }
        if (pacienteRepository.existsByDniAndIdentidadVerificadaTrue(paciente.getDni())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya hay otro paciente con la identidad verificada para ese DNI. Si esta cuenta es una suplantación, desactivala");
        }
        paciente.setIdentidadVerificada(true);
        return PacienteResponse.from(pacienteRepository.save(paciente));
    }
}
