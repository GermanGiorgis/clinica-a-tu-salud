package com.germanggiorgis.clinica.repository;

import com.germanggiorgis.clinica.dto.PuntajeMedico;
import com.germanggiorgis.clinica.model.Encuesta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EncuestaRepository extends JpaRepository<Encuesta, Long> {
    Optional<Encuesta> findByTurnoId(Long turnoId);

    /** null si no hay ninguna encuesta. */
    @Query("select avg(e.puntaje) from Encuesta e")
    Double puntajePromedio();

    @Query("select new com.germanggiorgis.clinica.dto.PuntajeMedico(e.turno.medico.id, avg(e.puntaje), count(e)) "
            + "from Encuesta e group by e.turno.medico.id")
    List<PuntajeMedico> puntajePorMedico();
}
