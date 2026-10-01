package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.CargarResultadoRequest;
import com.germanggiorgis.clinica.dto.CrearOrdenEstudioRequest;
import com.germanggiorgis.clinica.dto.OrdenEstudioResponse;
import com.germanggiorgis.clinica.dto.ResultadoEstudioResponse;
import com.germanggiorgis.clinica.model.Consulta;
import com.germanggiorgis.clinica.model.EstadoOrdenEstudio;
import com.germanggiorgis.clinica.model.Estudio;
import com.germanggiorgis.clinica.model.OrdenEstudio;
import com.germanggiorgis.clinica.model.ResultadoEstudio;
import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Turno;
import com.germanggiorgis.clinica.model.Usuario;
import com.germanggiorgis.clinica.repository.ConsultaRepository;
import com.germanggiorgis.clinica.repository.EstudioRepository;
import com.germanggiorgis.clinica.repository.OrdenEstudioRepository;
import com.germanggiorgis.clinica.repository.ResultadoEstudioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Una orden de estudio cuelga de una Consulta; la pide y carga el resultado el médico del turno o un admin. */
@Service
@RequiredArgsConstructor
@Transactional
public class OrdenEstudioService {

    private final OrdenEstudioRepository ordenEstudioRepository;
    private final ResultadoEstudioRepository resultadoEstudioRepository;
    private final ConsultaRepository consultaRepository;
    private final EstudioRepository estudioRepository;
    private final UsuarioContextService usuarioContextService;

    public OrdenEstudioResponse crear(Long consultaId, CrearOrdenEstudioRequest request) {
        Usuario actual = usuarioContextService.getUsuarioActual();
        Consulta consulta = obtenerConsulta(consultaId);
        verificarEsMedicoDelTurnoOAdmin(actual, consulta.getTurno());

        Estudio estudio = estudioRepository.findById(request.estudioId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Estudio no encontrado"));

        OrdenEstudio orden = ordenEstudioRepository.save(OrdenEstudio.builder()
                .consulta(consulta)
                .estudio(estudio)
                .indicaciones(request.indicaciones())
                .build());

        return OrdenEstudioResponse.from(orden, null);
    }

    public OrdenEstudioResponse cargarResultado(Long ordenId, CargarResultadoRequest request) {
        Usuario actual = usuarioContextService.getUsuarioActual();
        OrdenEstudio orden = ordenEstudioRepository.findById(ordenId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden de estudio no encontrada"));
        verificarEsMedicoDelTurnoOAdmin(actual, orden.getConsulta().getTurno());

        if (orden.getEstado() != EstadoOrdenEstudio.SOLICITADA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Esa orden ya tiene un resultado cargado");
        }

        ResultadoEstudio resultado = resultadoEstudioRepository.save(ResultadoEstudio.builder()
                .ordenEstudio(orden)
                .resultado(request.resultado())
                .build());

        orden.setEstado(EstadoOrdenEstudio.COMPLETADA);
        ordenEstudioRepository.save(orden);

        return OrdenEstudioResponse.from(orden, ResultadoEstudioResponse.from(resultado));
    }

    private void verificarEsMedicoDelTurnoOAdmin(Usuario actual, Turno turno) {
        boolean ok = actual.getRol() == Rol.ADMIN
                || (actual.getRol() == Rol.MEDICO && turno.getMedico().getUsuario().getId().equals(actual.getId()));
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el médico del turno o un admin puede gestionar esta orden");
        }
    }

    private Consulta obtenerConsulta(Long id) {
        return consultaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consulta no encontrada"));
    }
}
