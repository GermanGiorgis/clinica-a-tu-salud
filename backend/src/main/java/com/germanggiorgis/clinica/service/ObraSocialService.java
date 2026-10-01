package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.ObraSocialRequest;
import com.germanggiorgis.clinica.dto.ObraSocialResponse;
import com.germanggiorgis.clinica.model.ObraSocial;
import com.germanggiorgis.clinica.repository.ObraSocialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ObraSocialService {

    private final ObraSocialRepository obraSocialRepository;

    public List<ObraSocialResponse> listar() {
        return obraSocialRepository.findAll().stream().map(ObraSocialResponse::from).toList();
    }

    public ObraSocialResponse crear(ObraSocialRequest request) {
        if (obraSocialRepository.existsByNombreIgnoreCase(request.nombre())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una obra social con ese nombre");
        }
        ObraSocial obraSocial = ObraSocial.builder().nombre(request.nombre()).telefono(request.telefono()).build();
        return ObraSocialResponse.from(obraSocialRepository.save(obraSocial));
    }

    public ObraSocialResponse actualizar(Long id, ObraSocialRequest request) {
        ObraSocial obraSocial = obtener(id);
        if (obraSocialRepository.existsByNombreIgnoreCaseAndIdNot(request.nombre(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una obra social con ese nombre");
        }
        obraSocial.setNombre(request.nombre());
        obraSocial.setTelefono(request.telefono());
        return ObraSocialResponse.from(obraSocialRepository.save(obraSocial));
    }

    public void eliminar(Long id) {
        obraSocialRepository.delete(obtener(id));
    }

    private ObraSocial obtener(Long id) {
        return obraSocialRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Obra social no encontrada"));
    }
}
