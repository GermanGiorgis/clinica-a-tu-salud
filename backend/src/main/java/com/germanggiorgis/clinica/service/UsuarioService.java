package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.CrearUsuarioRequest;
import com.germanggiorgis.clinica.dto.UsuarioResponse;
import com.germanggiorgis.clinica.model.Especialidad;
import com.germanggiorgis.clinica.model.Medico;
import com.germanggiorgis.clinica.model.Paciente;
import com.germanggiorgis.clinica.model.Usuario;
import com.germanggiorgis.clinica.repository.EspecialidadRepository;
import com.germanggiorgis.clinica.repository.MedicoRepository;
import com.germanggiorgis.clinica.repository.PacienteRepository;
import com.germanggiorgis.clinica.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

/**
 * Lo usa un ADMIN para crear cualquier tipo de cuenta (ver
 * CrearUsuarioRequest). El autorregistro público de pacientes vive en
 * AuthService, no acá.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PacienteRepository pacienteRepository;
    private final MedicoRepository medicoRepository;
    private final EspecialidadRepository especialidadRepository;
    private final PasswordEncoder passwordEncoder;

    public List<UsuarioResponse> listar() {
        return usuarioRepository.findAll().stream().map(UsuarioResponse::from).toList();
    }

    public UsuarioResponse crear(CrearUsuarioRequest request) {
        if (usuarioRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una cuenta con ese email");
        }

        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .rol(request.rol())
                .nombre(request.nombre())
                .apellido(request.apellido())
                .telefono(request.telefono())
                .build());

        Long perfilId = switch (request.rol()) {
            case PACIENTE -> crearPerfilPaciente(usuario, request);
            case MEDICO -> crearPerfilMedico(usuario, request);
            case ASISTENTE, ADMIN -> null;
        };

        return UsuarioResponse.from(usuario, perfilId);
    }

    private Long crearPerfilPaciente(Usuario usuario, CrearUsuarioRequest request) {
        if (request.dni() == null || request.dni().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el DNI del paciente");
        }
        if (request.fechaNacimiento() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta la fecha de nacimiento del paciente");
        }
        if (pacienteRepository.findByDni(request.dni()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un paciente con ese DNI");
        }
        Paciente paciente = pacienteRepository.save(Paciente.builder()
                .usuario(usuario)
                .dni(request.dni())
                .fechaNacimiento(request.fechaNacimiento())
                .build());
        return paciente.getId();
    }

    private Long crearPerfilMedico(Usuario usuario, CrearUsuarioRequest request) {
        if (request.matricula() == null || request.matricula().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta la matrícula del médico");
        }
        if (medicoRepository.findByMatricula(request.matricula()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un médico con esa matrícula");
        }

        List<Especialidad> especialidades = new ArrayList<>();
        if (request.especialidadIds() != null) {
            for (Long id : request.especialidadIds()) {
                especialidades.add(especialidadRepository.findById(id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Especialidad " + id + " no encontrada")));
            }
        }

        Medico medico = medicoRepository.save(Medico.builder()
                .usuario(usuario)
                .matricula(request.matricula())
                .especialidades(especialidades)
                .build());
        return medico.getId();
    }
}
