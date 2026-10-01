package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.MotivoCancelacionRequest;
import com.germanggiorgis.clinica.dto.MotivoCancelacionResponse;
import com.germanggiorgis.clinica.model.MotivoCancelacion;
import com.germanggiorgis.clinica.repository.MotivoCancelacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MotivoCancelacionService {

    private final MotivoCancelacionRepository motivoCancelacionRepository;

    public List<MotivoCancelacionResponse> listar() {
        return motivoCancelacionRepository.findAll().stream()
                .map(MotivoCancelacionResponse::from)
                .toList();
    }

    public MotivoCancelacionResponse crear(MotivoCancelacionRequest request) {
        if (motivoCancelacionRepository.existsByNombreIgnoreCase(request.nombre())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un motivo de cancelación con ese nombre");
        }
        MotivoCancelacion motivo = MotivoCancelacion.builder()
                .nombre(request.nombre())
                .descripcion(request.descripcion())
                .build();
        return MotivoCancelacionResponse.from(motivoCancelacionRepository.save(motivo));
    }

    public MotivoCancelacionResponse actualizar(Long id, MotivoCancelacionRequest request) {
        MotivoCancelacion motivo = obtener(id);
        if (motivoCancelacionRepository.existsByNombreIgnoreCaseAndIdNot(request.nombre(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un motivo de cancelación con ese nombre");
        }
        motivo.setNombre(request.nombre());
        motivo.setDescripcion(request.descripcion());
        return MotivoCancelacionResponse.from(motivoCancelacionRepository.save(motivo));
    }

    public void eliminar(Long id) {
        motivoCancelacionRepository.delete(obtener(id));
    }

    private MotivoCancelacion obtener(Long id) {
        return motivoCancelacionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Motivo de cancelación no encontrado"));
    }
}
