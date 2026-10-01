package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.MedicamentoRequest;
import com.germanggiorgis.clinica.dto.MedicamentoResponse;
import com.germanggiorgis.clinica.model.Medicamento;
import com.germanggiorgis.clinica.repository.MedicamentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MedicamentoService {

    private final MedicamentoRepository medicamentoRepository;

    public List<MedicamentoResponse> listar() {
        return medicamentoRepository.findAll().stream().map(MedicamentoResponse::from).toList();
    }

    public MedicamentoResponse crear(MedicamentoRequest request) {
        if (medicamentoRepository.existsByNombreIgnoreCase(request.nombre())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un medicamento con ese nombre");
        }
        Medicamento medicamento = Medicamento.builder()
                .nombre(request.nombre())
                .presentacion(request.presentacion())
                .build();
        return MedicamentoResponse.from(medicamentoRepository.save(medicamento));
    }

    public MedicamentoResponse actualizar(Long id, MedicamentoRequest request) {
        Medicamento medicamento = obtener(id);
        if (medicamentoRepository.existsByNombreIgnoreCaseAndIdNot(request.nombre(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un medicamento con ese nombre");
        }
        medicamento.setNombre(request.nombre());
        medicamento.setPresentacion(request.presentacion());
        return MedicamentoResponse.from(medicamentoRepository.save(medicamento));
    }

    public void eliminar(Long id) {
        medicamentoRepository.delete(obtener(id));
    }

    private Medicamento obtener(Long id) {
        return medicamentoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medicamento no encontrado"));
    }
}
