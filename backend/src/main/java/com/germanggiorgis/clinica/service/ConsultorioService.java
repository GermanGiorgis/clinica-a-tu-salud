package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.ConsultorioRequest;
import com.germanggiorgis.clinica.dto.ConsultorioResponse;
import com.germanggiorgis.clinica.model.Consultorio;
import com.germanggiorgis.clinica.repository.ConsultorioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConsultorioService {

    private final ConsultorioRepository consultorioRepository;

    public List<ConsultorioResponse> listar() {
        return consultorioRepository.findAll().stream()
                .map(ConsultorioResponse::from)
                .toList();
    }

    public ConsultorioResponse crear(ConsultorioRequest request) {
        if (consultorioRepository.existsByNombreIgnoreCase(request.nombre())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un consultorio con ese nombre");
        }
        Consultorio consultorio = Consultorio.builder()
                .nombre(request.nombre())
                .ubicacion(request.ubicacion())
                .piso(request.piso())
                .build();
        return ConsultorioResponse.from(consultorioRepository.save(consultorio));
    }

    public ConsultorioResponse actualizar(Long id, ConsultorioRequest request) {
        Consultorio consultorio = obtener(id);
        if (consultorioRepository.existsByNombreIgnoreCaseAndIdNot(request.nombre(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un consultorio con ese nombre");
        }
        consultorio.setNombre(request.nombre());
        consultorio.setUbicacion(request.ubicacion());
        consultorio.setPiso(request.piso());
        return ConsultorioResponse.from(consultorioRepository.save(consultorio));
    }

    public void eliminar(Long id) {
        consultorioRepository.delete(obtener(id));
    }

    private Consultorio obtener(Long id) {
        return consultorioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consultorio no encontrado"));
    }
}
