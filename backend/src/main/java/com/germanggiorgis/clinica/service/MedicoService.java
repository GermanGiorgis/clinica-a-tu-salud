package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.MedicoResponse;
import com.germanggiorgis.clinica.model.Medico;
import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.repository.MedicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Público: un paciente navega médicos antes incluso de loguearse. Un médico desactivado no aparece ni se
 * puede abrir para el público (no se le puede reservar); el personal de mostrador sí puede verlo, para
 * poder cerrar o cancelar los turnos que le quedaron.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MedicoService {

    private final MedicoRepository medicoRepository;
    private final UsuarioContextService usuarioContextService;

    /**
     * Si se pasa especialidadId, filtra (es lo que usa la pantalla de cada especialidad).
     * {@code incluirInactivos} solo se respeta para admin y recepción; para cualquier otra persona se ignora.
     */
    public List<MedicoResponse> listar(Long especialidadId, boolean incluirInactivos) {
        boolean conInactivos = incluirInactivos && esPersonalDeMostrador();
        List<Medico> medicos;
        if (especialidadId != null) {
            medicos = conInactivos
                    ? medicoRepository.findByEspecialidades_Id(especialidadId)
                    : medicoRepository.findByEspecialidades_IdAndUsuarioActivoTrue(especialidadId);
        } else {
            medicos = conInactivos ? medicoRepository.findAll() : medicoRepository.findByUsuarioActivoTrue();
        }
        return medicos.stream().map(MedicoResponse::from).toList();
    }

    public MedicoResponse obtener(Long id) {
        Medico medico = obtenerEntidad(id);
        if (!medico.getUsuario().isActivo() && !esPersonalDeMostrador()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Médico no encontrado");
        }
        return MedicoResponse.from(medico);
    }

    Medico obtenerEntidad(Long id) {
        return medicoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Médico no encontrado"));
    }

    private boolean esPersonalDeMostrador() {
        return usuarioContextService.getUsuarioActualSiHay()
                .map(u -> u.getRol() == Rol.ADMIN || u.getRol() == Rol.RECEPCIONISTA)
                .orElse(false);
    }
}
