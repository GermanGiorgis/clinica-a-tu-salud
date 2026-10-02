package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.CrearFacturaRequest;
import com.germanggiorgis.clinica.dto.FacturaResponse;
import com.germanggiorgis.clinica.dto.ItemFacturaRequest;
import com.germanggiorgis.clinica.model.Consultorio;
import com.germanggiorgis.clinica.model.EstadoTurno;
import com.germanggiorgis.clinica.model.Factura;
import com.germanggiorgis.clinica.model.ItemFactura;
import com.germanggiorgis.clinica.model.MedioPago;
import com.germanggiorgis.clinica.model.Medico;
import com.germanggiorgis.clinica.model.Paciente;
import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Turno;
import com.germanggiorgis.clinica.model.Usuario;
import com.germanggiorgis.clinica.repository.FacturaRepository;
import com.germanggiorgis.clinica.repository.ItemFacturaRepository;
import com.germanggiorgis.clinica.repository.TurnoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.germanggiorgis.clinica.service.HttpStatusAssert.assertStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FacturaServiceTest {

    @Mock
    private FacturaRepository facturaRepository;
    @Mock
    private ItemFacturaRepository itemFacturaRepository;
    @Mock
    private TurnoRepository turnoRepository;
    @Mock
    private UsuarioContextService usuarioContextService;

    @InjectMocks
    private FacturaService facturaService;

    private Usuario usuario(long id, Rol rol) {
        return Usuario.builder().id(id).email("u" + id + "@clinica.test").rol(rol).nombre("N").apellido("A").activo(true).build();
    }

    private Turno turno(Usuario medicoUsuario, Usuario pacienteUsuario, EstadoTurno estado) {
        Medico medico = Medico.builder().id(1L).usuario(medicoUsuario).matricula("MP-1").especialidades(List.of()).build();
        Paciente paciente = Paciente.builder().id(1L).usuario(pacienteUsuario).dni("1").build();
        return Turno.builder().id(5L).medico(medico).paciente(paciente)
                .consultorio(Consultorio.builder().id(1L).nombre("C").build())
                .creadoPor(pacienteUsuario).inicio(LocalDateTime.now()).fin(LocalDateTime.now().plusMinutes(30)).estado(estado).build();
    }

    private CrearFacturaRequest request() {
        return new CrearFacturaRequest(MedioPago.OBRA_SOCIAL, List.of(
                new ItemFacturaRequest("Consulta", new BigDecimal("15000.00")),
                new ItemFacturaRequest("Hemograma", new BigDecimal("8500.50"))));
    }

    @Test
    void crear_calcula_el_total_sumando_los_items() {
        Usuario usuarioAdmin = usuario(9L, Rol.ADMIN);
        Turno turno = turno(usuario(1L, Rol.MEDICO), usuario(2L, Rol.PACIENTE), EstadoTurno.ATENDIDO);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioAdmin);
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));
        when(facturaRepository.findByTurnoId(5L)).thenReturn(Optional.empty());
        when(facturaRepository.save(any(Factura.class))).thenAnswer(inv -> {
            Factura f = inv.getArgument(0);
            f.setId(1L);
            return f;
        });
        when(itemFacturaRepository.save(any(ItemFactura.class))).thenAnswer(inv -> inv.getArgument(0));

        FacturaResponse respuesta = facturaService.crear(5L, request());

        assertThat(respuesta.total()).isEqualByComparingTo("23500.50");
        assertThat(respuesta.items()).hasSize(2);
        assertThat(respuesta.medioPago()).isEqualTo(MedioPago.OBRA_SOCIAL);
    }

    @Test
    void crear_lo_permite_al_medico_del_turno() {
        Usuario usuarioMedico = usuario(1L, Rol.MEDICO);
        Turno turno = turno(usuarioMedico, usuario(2L, Rol.PACIENTE), EstadoTurno.ATENDIDO);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioMedico);
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));
        when(facturaRepository.findByTurnoId(5L)).thenReturn(Optional.empty());
        when(facturaRepository.save(any(Factura.class))).thenAnswer(inv -> inv.getArgument(0));
        when(itemFacturaRepository.save(any(ItemFactura.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(facturaService.crear(5L, request())).isNotNull();
    }

    @Test
    void crear_rechaza_a_un_paciente() {
        Usuario usuarioPaciente = usuario(2L, Rol.PACIENTE);
        Turno turno = turno(usuario(1L, Rol.MEDICO), usuarioPaciente, EstadoTurno.ATENDIDO);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente);
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));

        assertStatus(() -> facturaService.crear(5L, request()), HttpStatus.FORBIDDEN);
    }

    @Test
    void crear_rechaza_a_un_medico_que_no_es_el_del_turno() {
        Turno turno = turno(usuario(1L, Rol.MEDICO), usuario(2L, Rol.PACIENTE), EstadoTurno.ATENDIDO);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuario(999L, Rol.MEDICO));
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));

        assertStatus(() -> facturaService.crear(5L, request()), HttpStatus.FORBIDDEN);
    }

    @Test
    void crear_rechaza_un_turno_que_no_esta_atendido() {
        Usuario usuarioAdmin = usuario(9L, Rol.ADMIN);
        Turno turno = turno(usuario(1L, Rol.MEDICO), usuario(2L, Rol.PACIENTE), EstadoTurno.CONFIRMADO);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioAdmin);
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));

        assertStatus(() -> facturaService.crear(5L, request()), HttpStatus.BAD_REQUEST);
    }

    @Test
    void crear_rechaza_facturar_dos_veces_el_mismo_turno() {
        Usuario usuarioAdmin = usuario(9L, Rol.ADMIN);
        Turno turno = turno(usuario(1L, Rol.MEDICO), usuario(2L, Rol.PACIENTE), EstadoTurno.ATENDIDO);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioAdmin);
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));
        when(facturaRepository.findByTurnoId(5L)).thenReturn(Optional.of(
                Factura.builder().id(1L).turno(turno).total(BigDecimal.TEN).medioPago(MedioPago.EFECTIVO).build()));

        assertStatus(() -> facturaService.crear(5L, request()), HttpStatus.CONFLICT);
    }

    @Test
    void obtenerPorTurno_lo_permite_al_paciente_dueno() {
        Usuario usuarioPaciente = usuario(2L, Rol.PACIENTE);
        Turno turno = turno(usuario(1L, Rol.MEDICO), usuarioPaciente, EstadoTurno.ATENDIDO);
        Factura factura = Factura.builder().id(1L).turno(turno).total(new BigDecimal("100.00")).medioPago(MedioPago.EFECTIVO).build();
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioPaciente);
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));
        when(facturaRepository.findByTurnoId(5L)).thenReturn(Optional.of(factura));
        when(itemFacturaRepository.findByFacturaId(1L)).thenReturn(List.of());

        assertThat(facturaService.obtenerPorTurno(5L).total()).isEqualByComparingTo("100.00");
    }

    @Test
    void obtenerPorTurno_rechaza_a_un_tercero() {
        Turno turno = turno(usuario(1L, Rol.MEDICO), usuario(2L, Rol.PACIENTE), EstadoTurno.ATENDIDO);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuario(999L, Rol.PACIENTE));
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));

        assertStatus(() -> facturaService.obtenerPorTurno(5L), HttpStatus.FORBIDDEN);
    }

    @Test
    void obtenerPorTurno_404_si_todavia_no_hay_factura() {
        Usuario usuarioAdmin = usuario(9L, Rol.ADMIN);
        Turno turno = turno(usuario(1L, Rol.MEDICO), usuario(2L, Rol.PACIENTE), EstadoTurno.ATENDIDO);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioAdmin);
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turno));
        when(facturaRepository.findByTurnoId(5L)).thenReturn(Optional.empty());

        assertStatus(() -> facturaService.obtenerPorTurno(5L), HttpStatus.NOT_FOUND);
    }
}
