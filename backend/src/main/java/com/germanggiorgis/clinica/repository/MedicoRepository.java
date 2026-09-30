package com.germanggiorgis.clinica.repository;

import com.germanggiorgis.clinica.model.Medico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MedicoRepository extends JpaRepository<Medico, Long> {
    Optional<Medico> findByUsuarioId(Long usuarioId);
    Optional<Medico> findByMatricula(String matricula);
}
