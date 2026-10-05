package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.AccesoHistorialResponse;
import com.germanggiorgis.clinica.repository.AccesoHistorialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Consulta del registro de accesos a historias clínicas (solo ADMIN, lo exige SecurityConfig). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditoriaService {

    private final AccesoHistorialRepository accesoHistorialRepository;

    /** Los 200 accesos más recientes; de un paciente si se indica, o de toda la clínica. */
    public List<AccesoHistorialResponse> accesosHistorial(Long pacienteId) {
        var accesos = pacienteId != null
                ? accesoHistorialRepository.findTop200ByPacienteIdOrderByAccedidoEnDesc(pacienteId)
                : accesoHistorialRepository.findTop200ByOrderByAccedidoEnDesc();
        return accesos.stream().map(AccesoHistorialResponse::from).toList();
    }
}
