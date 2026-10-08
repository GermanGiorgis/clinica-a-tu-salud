package com.germanggiorgis.clinica.repository;

import com.germanggiorgis.clinica.model.Medico;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MedicoRepository extends JpaRepository<Medico, Long> {
    Optional<Medico> findByUsuarioId(Long usuarioId);
    Optional<Medico> findByMatricula(String matricula);
    // Las especialidades (una colección) se cargan en bloque con hibernate.default_batch_fetch_size.
    @EntityGraph(attributePaths = "usuario")
    List<Medico> findByEspecialidades_Id(Long especialidadId);

    @EntityGraph(attributePaths = "usuario")
    List<Medico> findByEspecialidades_IdAndUsuarioActivoTrue(Long especialidadId);

    @EntityGraph(attributePaths = "usuario")
    List<Medico> findByUsuarioActivoTrue();

    @Override
    @EntityGraph(attributePaths = "usuario")
    List<Medico> findAll();
}
