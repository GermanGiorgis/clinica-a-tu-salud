package com.germanggiorgis.clinica.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "orden_estudio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrdenEstudio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consulta_id", nullable = false)
    private Consulta consulta;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estudio_id", nullable = false)
    private Estudio estudio;

    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private EstadoOrdenEstudio estado = EstadoOrdenEstudio.SOLICITADA;

    @Column(length = 500)
    private String indicaciones;

    @Builder.Default
    @Column(name = "emitida_en", nullable = false, updatable = false)
    private LocalDateTime emitidaEn = LocalDateTime.now();
}
