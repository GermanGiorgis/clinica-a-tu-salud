package com.germanggiorgis.clinica.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "resultado_estudio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResultadoEstudio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orden_estudio_id", nullable = false, unique = true)
    private OrdenEstudio ordenEstudio;

    @NotBlank
    @Column(columnDefinition = "TEXT", nullable = false)
    private String resultado;

    @Builder.Default
    @Column(name = "cargado_en", nullable = false, updatable = false)
    private LocalDateTime cargadoEn = LocalDateTime.now();
}
