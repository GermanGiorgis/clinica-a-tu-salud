package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.EstudioRequest;
import com.germanggiorgis.clinica.dto.EstudioResponse;
import com.germanggiorgis.clinica.model.Estudio;
import com.germanggiorgis.clinica.repository.EstudioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EstudioService {

    private final EstudioRepository estudioRepository;

    public List<EstudioResponse> listar() {
        return estudioRepository.findAll().stream().map(EstudioResponse::from).toList();
    }

    public EstudioResponse crear(EstudioRequest request) {
        if (estudioRepository.existsByNombreIgnoreCase(request.nombre())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un estudio con ese nombre");
        }
        Estudio estudio = Estudio.builder().nombre(request.nombre()).tipo(request.tipo()).build();
        return EstudioResponse.from(estudioRepository.save(estudio));
    }

    public EstudioResponse actualizar(Long id, EstudioRequest request) {
        Estudio estudio = obtener(id);
        if (estudioRepository.existsByNombreIgnoreCaseAndIdNot(request.nombre(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un estudio con ese nombre");
        }
        estudio.setNombre(request.nombre());
        estudio.setTipo(request.tipo());
        return EstudioResponse.from(estudioRepository.save(estudio));
    }

    public void eliminar(Long id) {
        estudioRepository.delete(obtener(id));
    }

    private Estudio obtener(Long id) {
        return estudioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Estudio no encontrado"));
    }
}
