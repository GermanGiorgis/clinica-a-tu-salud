package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.HorarioAtencionRequest;
import com.germanggiorgis.clinica.dto.HorarioAtencionResponse;
import com.germanggiorgis.clinica.model.Consultorio;
import com.germanggiorgis.clinica.model.HorarioAtencion;
import com.germanggiorgis.clinica.model.Medico;
import com.germanggiorgis.clinica.model.Usuario;
import com.germanggiorgis.clinica.repository.ConsultorioRepository;
import com.germanggiorgis.clinica.repository.HorarioAtencionRepository;
import com.germanggiorgis.clinica.repository.MedicoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static com.germanggiorgis.clinica.service.HttpStatusAssert.assertStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HorarioAtencionServiceTest {

    @Mock
    private HorarioAtencionRepository horarioAtencionRepository;
    @Mock
    private MedicoRepository medicoRepository;
    @Mock
    private ConsultorioRepository consultorioRepository;

    @InjectMocks
    private HorarioAtencionService horarioAtencionService;

    private Medico medico(long id) {
        Usuario usuario = Usuario.builder().id(id).email("m" + id + "@clinica.test").rol(com.germanggiorgis.clinica.model.Rol.MEDICO)
                .nombre("N").apellido("A").activo(true).build();
        return Medico.builder().id(id).usuario(usuario).matricula("MP-" + id).especialidades(List.of()).build();
    }

    private Consultorio consultorio(long id) {
        return Consultorio.builder().id(id).nombre("C" + id).build();
    }

    private HorarioAtencionRequest request(long medicoId, long consultorioId, LocalTime inicio, LocalTime fin) {
        return new HorarioAtencionRequest(medicoId, consultorioId, DayOfWeek.MONDAY, inicio, fin, 30,
                LocalDate.of(2026, 1, 1), null);
    }

    @Test
    void crear_horario_valido() {
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico(1L)));
        when(consultorioRepository.findById(1L)).thenReturn(Optional.of(consultorio(1L)));
        when(horarioAtencionRepository.findByMedicoIdAndDiaSemana(1L, DayOfWeek.MONDAY)).thenReturn(List.of());
        when(horarioAtencionRepository.findByConsultorioIdAndDiaSemana(1L, DayOfWeek.MONDAY)).thenReturn(List.of());
        when(horarioAtencionRepository.save(any(HorarioAtencion.class))).thenAnswer(inv -> {
            HorarioAtencion h = inv.getArgument(0);
            h.setId(1L);
            return h;
        });

        HorarioAtencionResponse respuesta = horarioAtencionService.crear(request(1L, 1L, LocalTime.of(9, 0), LocalTime.of(12, 0)));

        assertThat(respuesta.id()).isEqualTo(1L);
    }

    @Test
    void crear_rechaza_hora_fin_anterior_a_inicio() {
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico(1L)));
        when(consultorioRepository.findById(1L)).thenReturn(Optional.of(consultorio(1L)));

        assertStatus(() -> horarioAtencionService.crear(request(1L, 1L, LocalTime.of(12, 0), LocalTime.of(9, 0))),
                HttpStatus.BAD_REQUEST);
    }

    @Test
    void crear_rechaza_superposicion_del_mismo_medico() {
        HorarioAtencion existente = HorarioAtencion.builder().id(5L).medico(medico(1L)).consultorio(consultorio(2L))
                .diaSemana(DayOfWeek.MONDAY).horaInicio(LocalTime.of(10, 0)).horaFin(LocalTime.of(13, 0))
                .duracionTurnoMin(30).vigenteDesde(LocalDate.of(2026, 1, 1)).build();

        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico(1L)));
        when(consultorioRepository.findById(1L)).thenReturn(Optional.of(consultorio(1L)));
        when(horarioAtencionRepository.findByMedicoIdAndDiaSemana(1L, DayOfWeek.MONDAY)).thenReturn(List.of(existente));

        assertStatus(() -> horarioAtencionService.crear(request(1L, 1L, LocalTime.of(9, 0), LocalTime.of(11, 0))),
                HttpStatus.CONFLICT);
    }

    @Test
    void crear_rechaza_consultorio_ocupado_por_otro_medico() {
        HorarioAtencion existente = HorarioAtencion.builder().id(5L).medico(medico(2L)).consultorio(consultorio(1L))
                .diaSemana(DayOfWeek.MONDAY).horaInicio(LocalTime.of(10, 0)).horaFin(LocalTime.of(13, 0))
                .duracionTurnoMin(30).vigenteDesde(LocalDate.of(2026, 1, 1)).build();

        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico(1L)));
        when(consultorioRepository.findById(1L)).thenReturn(Optional.of(consultorio(1L)));
        when(horarioAtencionRepository.findByMedicoIdAndDiaSemana(1L, DayOfWeek.MONDAY)).thenReturn(List.of());
        when(horarioAtencionRepository.findByConsultorioIdAndDiaSemana(1L, DayOfWeek.MONDAY)).thenReturn(List.of(existente));

        assertStatus(() -> horarioAtencionService.crear(request(1L, 1L, LocalTime.of(9, 0), LocalTime.of(11, 0))),
                HttpStatus.CONFLICT);
    }

    @Test
    void crear_no_superponerse_consigo_mismo_no_es_conflicto() {
        // Dos franjas que no se tocan (9-11 y 11-13) en el mismo dia no deben chocar.
        HorarioAtencion existente = HorarioAtencion.builder().id(5L).medico(medico(1L)).consultorio(consultorio(1L))
                .diaSemana(DayOfWeek.MONDAY).horaInicio(LocalTime.of(11, 0)).horaFin(LocalTime.of(13, 0))
                .duracionTurnoMin(30).vigenteDesde(LocalDate.of(2026, 1, 1)).build();

        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico(1L)));
        when(consultorioRepository.findById(1L)).thenReturn(Optional.of(consultorio(1L)));
        when(horarioAtencionRepository.findByMedicoIdAndDiaSemana(1L, DayOfWeek.MONDAY)).thenReturn(List.of(existente));
        when(horarioAtencionRepository.findByConsultorioIdAndDiaSemana(1L, DayOfWeek.MONDAY)).thenReturn(List.of(existente));
        when(horarioAtencionRepository.save(any(HorarioAtencion.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(horarioAtencionService.crear(request(1L, 1L, LocalTime.of(9, 0), LocalTime.of(11, 0)))).isNotNull();
    }

    @Test
    void actualizar_se_ignora_a_si_mismo_al_chequear_superposicion() {
        HorarioAtencion propio = HorarioAtencion.builder().id(5L).medico(medico(1L)).consultorio(consultorio(1L))
                .diaSemana(DayOfWeek.MONDAY).horaInicio(LocalTime.of(9, 0)).horaFin(LocalTime.of(11, 0))
                .duracionTurnoMin(30).vigenteDesde(LocalDate.of(2026, 1, 1)).build();

        when(horarioAtencionRepository.findById(5L)).thenReturn(Optional.of(propio));
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico(1L)));
        when(consultorioRepository.findById(1L)).thenReturn(Optional.of(consultorio(1L)));
        when(horarioAtencionRepository.findByMedicoIdAndDiaSemana(1L, DayOfWeek.MONDAY)).thenReturn(List.of(propio));
        when(horarioAtencionRepository.findByConsultorioIdAndDiaSemana(1L, DayOfWeek.MONDAY)).thenReturn(List.of(propio));
        when(horarioAtencionRepository.save(any(HorarioAtencion.class))).thenAnswer(inv -> inv.getArgument(0));

        // Mismo horario (9-11), no debería chocar contra si mismo.
        HorarioAtencionResponse respuesta = horarioAtencionService.actualizar(5L, request(1L, 1L, LocalTime.of(9, 0), LocalTime.of(11, 0)));

        assertThat(respuesta).isNotNull();
    }

    @Test
    void eliminar_horario_inexistente_es_404() {
        when(horarioAtencionRepository.findById(1L)).thenReturn(Optional.empty());
        assertStatus(() -> horarioAtencionService.eliminar(1L), HttpStatus.NOT_FOUND);
    }

    @Test
    void listar_filtra_por_medico_cuando_se_indica() {
        when(horarioAtencionRepository.findByMedicoId(1L)).thenReturn(List.of());
        assertThat(horarioAtencionService.listar(1L)).isEmpty();
    }

    @Test
    void listar_sin_filtro_devuelve_todos() {
        when(horarioAtencionRepository.findAll()).thenReturn(List.of());
        assertThat(horarioAtencionService.listar(null)).isEmpty();
    }
}
