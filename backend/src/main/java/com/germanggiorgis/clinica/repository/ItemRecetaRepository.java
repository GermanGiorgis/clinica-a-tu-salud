package com.germanggiorgis.clinica.repository;

import com.germanggiorgis.clinica.model.ItemReceta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemRecetaRepository extends JpaRepository<ItemReceta, Long> {
    List<ItemReceta> findByRecetaId(Long recetaId);
}
