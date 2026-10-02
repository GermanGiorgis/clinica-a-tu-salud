package com.germanggiorgis.clinica.repository;

import com.germanggiorgis.clinica.dto.MontoMedico;
import com.germanggiorgis.clinica.dto.MontoPorMedioPago;
import com.germanggiorgis.clinica.model.Factura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FacturaRepository extends JpaRepository<Factura, Long> {
    Optional<Factura> findByTurnoId(Long turnoId);

    @Query("select new com.germanggiorgis.clinica.dto.MontoPorMedioPago(f.medioPago, count(f), sum(f.total)) "
            + "from Factura f group by f.medioPago")
    List<MontoPorMedioPago> totalesPorMedioPago();

    @Query("select new com.germanggiorgis.clinica.dto.MontoMedico(f.turno.medico.id, sum(f.total)) "
            + "from Factura f group by f.turno.medico.id")
    List<MontoMedico> totalesPorMedico();
}
