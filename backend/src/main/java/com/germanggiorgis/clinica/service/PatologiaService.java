package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.PatologiaRequest;
import com.germanggiorgis.clinica.dto.PatologiaResponse;
import com.germanggiorgis.clinica.model.Especialidad;
import com.germanggiorgis.clinica.model.Patologia;
import com.germanggiorgis.clinica.repository.EspecialidadRepository;
import com.germanggiorgis.clinica.repository.PatologiaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * CRUD de patologías, como EspecialidadService pero con una relación a
 * Especialidad: @Transactional de clase evita el mismo
 * LazyInitializationException que ya apareció en HorarioAtencionService
 * (open-in-view está deliberadamente apagado).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PatologiaService {

    private final PatologiaRepository patologiaRepository;
    private final EspecialidadRepository especialidadRepository;

    @Transactional(readOnly = true)
    public List<PatologiaResponse> listar() {
        return patologiaRepository.findAll().stream().map(PatologiaResponse::from).toList();
    }

    public PatologiaResponse crear(PatologiaRequest request) {
        if (patologiaRepository.existsByNombreIgnoreCase(request.nombre())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una patología con ese nombre");
        }
        Patologia patologia = Patologia.builder()
                .nombre(request.nombre())
                .especialidad(obtenerEspecialidad(request.especialidadId()))
                .build();
        return PatologiaResponse.from(patologiaRepository.save(patologia));
    }

    public PatologiaResponse actualizar(Long id, PatologiaRequest request) {
        Patologia patologia = obtener(id);
        if (patologiaRepository.existsByNombreIgnoreCaseAndIdNot(request.nombre(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una patología con ese nombre");
        }
        patologia.setNombre(request.nombre());
        patologia.setEspecialidad(obtenerEspecialidad(request.especialidadId()));
        return PatologiaResponse.from(patologiaRepository.save(patologia));
    }

    public void eliminar(Long id) {
        patologiaRepository.delete(obtener(id));
    }

    private Especialidad obtenerEspecialidad(Long id) {
        return especialidadRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Especialidad no encontrada"));
    }

    private Patologia obtener(Long id) {
        return patologiaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patología no encontrada"));
    }
}
