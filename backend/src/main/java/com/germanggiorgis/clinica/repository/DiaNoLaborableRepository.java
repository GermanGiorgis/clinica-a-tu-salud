package com.germanggiorgis.clinica.repository;

import com.germanggiorgis.clinica.model.DiaNoLaborable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DiaNoLaborableRepository extends JpaRepository<DiaNoLaborable, Long> {

    /** Cierres de la clínica entera o de este médico que cubren la fecha. */
    @Query("select d from DiaNoLaborable d where d.desde <= :fecha and d.hasta >= :fecha "
            + "and (d.medico is null or d.medico.id = :medicoId) order by d.desde")
    List<DiaNoLaborable> cubrenFecha(@Param("medicoId") Long medicoId, @Param("fecha") LocalDate fecha);

    /** Los que todavía no terminaron (los ya pasados no interesan para operar), los más próximos primero. */
    @EntityGraph(attributePaths = {"medico.usuario"})
    List<DiaNoLaborable> findByHastaGreaterThanEqualOrderByDesdeAscIdAsc(LocalDate hoy);

    @Override
    @EntityGraph(attributePaths = {"medico.usuario"})
    Optional<DiaNoLaborable> findById(Long id);
}
