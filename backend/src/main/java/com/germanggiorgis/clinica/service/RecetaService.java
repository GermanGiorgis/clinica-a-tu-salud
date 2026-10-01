package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.CrearRecetaRequest;
import com.germanggiorgis.clinica.dto.ItemRecetaResponse;
import com.germanggiorgis.clinica.dto.RecetaResponse;
import com.germanggiorgis.clinica.model.Consulta;
import com.germanggiorgis.clinica.model.ItemReceta;
import com.germanggiorgis.clinica.model.Medicamento;
import com.germanggiorgis.clinica.model.Receta;
import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Turno;
import com.germanggiorgis.clinica.model.Usuario;
import com.germanggiorgis.clinica.repository.ConsultaRepository;
import com.germanggiorgis.clinica.repository.ItemRecetaRepository;
import com.germanggiorgis.clinica.repository.MedicamentoRepository;
import com.germanggiorgis.clinica.repository.RecetaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Una receta cuelga de una Consulta ya cargada; la emite el médico del turno o un admin. */
@Service
@RequiredArgsConstructor
@Transactional
public class RecetaService {

    private final RecetaRepository recetaRepository;
    private final ItemRecetaRepository itemRecetaRepository;
    private final ConsultaRepository consultaRepository;
    private final MedicamentoRepository medicamentoRepository;
    private final UsuarioContextService usuarioContextService;

    public RecetaResponse crear(Long consultaId, CrearRecetaRequest request) {
        Usuario actual = usuarioContextService.getUsuarioActual();
        Consulta consulta = obtenerConsulta(consultaId);
        verificarEsMedicoDelTurnoOAdmin(actual, consulta.getTurno());

        Receta receta = recetaRepository.save(Receta.builder()
                .consulta(consulta)
                .observaciones(request.observaciones())
                .build());

        List<ItemRecetaResponse> items = request.items().stream()
                .map(i -> itemRecetaRepository.save(ItemReceta.builder()
                        .receta(receta)
                        .medicamento(obtenerMedicamento(i.medicamentoId()))
                        .dosis(i.dosis())
                        .frecuencia(i.frecuencia())
                        .duracionDias(i.duracionDias())
                        .build()))
                .map(ItemRecetaResponse::from)
                .toList();

        return RecetaResponse.from(receta, items);
    }

    private Medicamento obtenerMedicamento(Long id) {
        return medicamentoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medicamento no encontrado"));
    }

    private void verificarEsMedicoDelTurnoOAdmin(Usuario actual, Turno turno) {
        boolean ok = actual.getRol() == Rol.ADMIN
                || (actual.getRol() == Rol.MEDICO && turno.getMedico().getUsuario().getId().equals(actual.getId()));
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el médico del turno o un admin puede emitir la receta");
        }
    }

    private Consulta obtenerConsulta(Long id) {
        return consultaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consulta no encontrada"));
    }
}
