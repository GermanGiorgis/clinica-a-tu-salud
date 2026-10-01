package com.germanggiorgis.clinica.repository;

import com.germanggiorgis.clinica.model.ItemFactura;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemFacturaRepository extends JpaRepository<ItemFactura, Long> {
    List<ItemFactura> findByFacturaId(Long facturaId);
}
