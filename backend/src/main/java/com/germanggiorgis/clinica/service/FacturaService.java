package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.CrearFacturaRequest;
import com.germanggiorgis.clinica.dto.FacturaResponse;
import com.germanggiorgis.clinica.dto.ItemFacturaRequest;
import com.germanggiorgis.clinica.dto.ItemFacturaResponse;
import com.germanggiorgis.clinica.model.EstadoTurno;
import com.germanggiorgis.clinica.model.Factura;
import com.germanggiorgis.clinica.model.ItemFactura;
import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Turno;
import com.germanggiorgis.clinica.model.Usuario;
import com.germanggiorgis.clinica.repository.FacturaRepository;
import com.germanggiorgis.clinica.repository.ItemFacturaRepository;
import com.germanggiorgis.clinica.repository.TurnoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

/**
 * Facturación simplificada (ver docs/modelo-datos.md): una Factura por
 * turno ya atendido, con sus ítems; el total se calcula acá, nunca se
 * confía en el que mande el cliente.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class FacturaService {

    private final FacturaRepository facturaRepository;
    private final ItemFacturaRepository itemFacturaRepository;
    private final TurnoRepository turnoRepository;
    private final UsuarioContextService usuarioContextService;

    public FacturaResponse crear(Long turnoId, CrearFacturaRequest request) {
        Usuario actual = usuarioContextService.getUsuarioActual();
        Turno turno = obtenerTurno(turnoId);
        verificarEsMedicoDelTurnoOAdmin(actual, turno);

        if (turno.getEstado() != EstadoTurno.ATENDIDO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se puede facturar un turno ya atendido");
        }
        if (facturaRepository.findByTurnoId(turnoId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese turno ya tiene una factura");
        }

        BigDecimal total = request.items().stream()
                .map(ItemFacturaRequest::importe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Factura factura = facturaRepository.save(Factura.builder()
                .turno(turno)
                .total(total)
                .medioPago(request.medioPago())
                .build());

        List<ItemFacturaResponse> items = request.items().stream()
                .map(i -> itemFacturaRepository.save(ItemFactura.builder()
                        .factura(factura)
                        .descripcion(i.descripcion())
                        .importe(i.importe())
                        .build()))
                .map(ItemFacturaResponse::from)
                .toList();

        return FacturaResponse.from(factura, items);
    }

    @Transactional(readOnly = true)
    public FacturaResponse obtenerPorTurno(Long turnoId) {
        Usuario actual = usuarioContextService.getUsuarioActual();
        Turno turno = obtenerTurno(turnoId);
        boolean puedeVer = actual.getRol() == Rol.ADMIN
                || (actual.getRol() == Rol.MEDICO && turno.getMedico().getUsuario().getId().equals(actual.getId()))
                || (actual.getRol() == Rol.PACIENTE && turno.getPaciente().getUsuario().getId().equals(actual.getId()));
        if (!puedeVer) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No podés ver esta factura");
        }
        Factura factura = facturaRepository.findByTurnoId(turnoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ese turno todavía no tiene factura"));
        List<ItemFacturaResponse> items = itemFacturaRepository.findByFacturaId(factura.getId()).stream()
                .map(ItemFacturaResponse::from)
                .toList();
        return FacturaResponse.from(factura, items);
    }

    private void verificarEsMedicoDelTurnoOAdmin(Usuario actual, Turno turno) {
        boolean ok = actual.getRol() == Rol.ADMIN
                || (actual.getRol() == Rol.MEDICO && turno.getMedico().getUsuario().getId().equals(actual.getId()));
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el médico del turno o un admin puede facturarlo");
        }
    }

    private Turno obtenerTurno(Long id) {
        return turnoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Turno no encontrado"));
    }
}
