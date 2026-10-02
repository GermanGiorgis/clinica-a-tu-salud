package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.CrearRecetaRequest;
import com.germanggiorgis.clinica.dto.ItemRecetaRequest;
import com.germanggiorgis.clinica.dto.RecetaResponse;
import com.germanggiorgis.clinica.model.Consulta;
import com.germanggiorgis.clinica.model.Consultorio;
import com.germanggiorgis.clinica.model.ItemReceta;
import com.germanggiorgis.clinica.model.Medicamento;
import com.germanggiorgis.clinica.model.Medico;
import com.germanggiorgis.clinica.model.Paciente;
import com.germanggiorgis.clinica.model.Receta;
import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Turno;
import com.germanggiorgis.clinica.model.Usuario;
import com.germanggiorgis.clinica.repository.ConsultaRepository;
import com.germanggiorgis.clinica.repository.ItemRecetaRepository;
import com.germanggiorgis.clinica.repository.MedicamentoRepository;
import com.germanggiorgis.clinica.repository.RecetaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.germanggiorgis.clinica.service.HttpStatusAssert.assertStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecetaServiceTest {

    @Mock
    private RecetaRepository recetaRepository;
    @Mock
    private ItemRecetaRepository itemRecetaRepository;
    @Mock
    private ConsultaRepository consultaRepository;
    @Mock
    private MedicamentoRepository medicamentoRepository;
    @Mock
    private UsuarioContextService usuarioContextService;

    @InjectMocks
    private RecetaService recetaService;

    private Usuario usuario(long id, Rol rol) {
        return Usuario.builder().id(id).email("u" + id + "@clinica.test").rol(rol).nombre("N").apellido("A").activo(true).build();
    }

    private Consulta consultaDe(Usuario medicoUsuario) {
        Medico medico = Medico.builder().id(1L).usuario(medicoUsuario).matricula("MP-1").especialidades(List.of()).build();
        Paciente paciente = Paciente.builder().id(1L).usuario(usuario(50L, Rol.PACIENTE)).dni("1").build();
        Turno turno = Turno.builder().id(1L).medico(medico).paciente(paciente).consultorio(Consultorio.builder().id(1L).nombre("C").build())
                .creadoPor(medicoUsuario).inicio(LocalDateTime.now()).fin(LocalDateTime.now().plusMinutes(30)).build();
        return Consulta.builder().id(1L).turno(turno).build();
    }

    @Test
    void crear_emite_una_receta_con_sus_items() {
        Usuario usuarioMedico = usuario(1L, Rol.MEDICO);
        Consulta consulta = consultaDe(usuarioMedico);
        Medicamento medicamento = Medicamento.builder().id(1L).nombre("Ibuprofeno").build();

        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioMedico);
        when(consultaRepository.findById(1L)).thenReturn(Optional.of(consulta));
        when(recetaRepository.save(any(Receta.class))).thenAnswer(inv -> {
            Receta r = inv.getArgument(0);
            r.setId(10L);
            return r;
        });
        when(medicamentoRepository.findById(1L)).thenReturn(Optional.of(medicamento));
        when(itemRecetaRepository.save(any(ItemReceta.class))).thenAnswer(inv -> {
            ItemReceta it = inv.getArgument(0);
            it.setId(1L);
            return it;
        });

        CrearRecetaRequest request = new CrearRecetaRequest("Tomar con comida",
                List.of(new ItemRecetaRequest(1L, "1 comp", "cada 8hs", 5)));
        RecetaResponse respuesta = recetaService.crear(1L, request);

        assertThat(respuesta.items()).hasSize(1);
        assertThat(respuesta.items().get(0).medicamentoNombre()).isEqualTo("Ibuprofeno");
    }

    @Test
    void crear_rechaza_a_un_medico_que_no_es_el_del_turno() {
        Consulta consulta = consultaDe(usuario(1L, Rol.MEDICO));
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuario(999L, Rol.MEDICO));
        when(consultaRepository.findById(1L)).thenReturn(Optional.of(consulta));

        CrearRecetaRequest request = new CrearRecetaRequest(null, List.of(new ItemRecetaRequest(1L, "1", "1", 1)));
        assertStatus(() -> recetaService.crear(1L, request), HttpStatus.FORBIDDEN);
    }

    @Test
    void crear_rechaza_un_medicamento_inexistente() {
        Usuario usuarioMedico = usuario(1L, Rol.MEDICO);
        Consulta consulta = consultaDe(usuarioMedico);
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuarioMedico);
        when(consultaRepository.findById(1L)).thenReturn(Optional.of(consulta));
        when(recetaRepository.save(any(Receta.class))).thenAnswer(inv -> inv.getArgument(0));
        when(medicamentoRepository.findById(99L)).thenReturn(Optional.empty());

        CrearRecetaRequest request = new CrearRecetaRequest(null, List.of(new ItemRecetaRequest(99L, "1", "1", 1)));
        assertStatus(() -> recetaService.crear(1L, request), HttpStatus.NOT_FOUND);
    }

    @Test
    void crear_rechaza_una_consulta_inexistente() {
        when(usuarioContextService.getUsuarioActual()).thenReturn(usuario(1L, Rol.MEDICO));
        when(consultaRepository.findById(1L)).thenReturn(Optional.empty());

        CrearRecetaRequest request = new CrearRecetaRequest(null, List.of(new ItemRecetaRequest(1L, "1", "1", 1)));
        assertStatus(() -> recetaService.crear(1L, request), HttpStatus.NOT_FOUND);
    }
}
