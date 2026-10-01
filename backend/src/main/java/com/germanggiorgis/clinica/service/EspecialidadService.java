package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.EspecialidadRequest;
import com.germanggiorgis.clinica.dto.EspecialidadResponse;
import com.germanggiorgis.clinica.model.Especialidad;
import com.germanggiorgis.clinica.repository.EspecialidadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EspecialidadService {

    private final EspecialidadRepository especialidadRepository;

    public List<EspecialidadResponse> listar() {
        return especialidadRepository.findAll().stream()
                .map(EspecialidadResponse::from)
                .toList();
    }

    public EspecialidadResponse crear(EspecialidadRequest request) {
        if (especialidadRepository.existsByNombreIgnoreCase(request.nombre())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una especialidad con ese nombre");
        }
        Especialidad especialidad = Especialidad.builder()
                .nombre(request.nombre())
                .descripcion(request.descripcion())
                .build();
        return EspecialidadResponse.from(especialidadRepository.save(especialidad));
    }

    public EspecialidadResponse actualizar(Long id, EspecialidadRequest request) {
        Especialidad especialidad = obtener(id);
        if (especialidadRepository.existsByNombreIgnoreCaseAndIdNot(request.nombre(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una especialidad con ese nombre");
        }
        especialidad.setNombre(request.nombre());
        especialidad.setDescripcion(request.descripcion());
        return EspecialidadResponse.from(especialidadRepository.save(especialidad));
    }

    public void eliminar(Long id) {
        especialidadRepository.delete(obtener(id));
    }

    private Especialidad obtener(Long id) {
        return especialidadRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Especialidad no encontrada"));
    }
}
