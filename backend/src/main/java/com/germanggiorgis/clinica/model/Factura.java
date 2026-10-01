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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 1:1 con Turno; facturación simplificada (ver docs/modelo-datos.md). */
@Entity
@Table(name = "factura")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Factura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "turno_id", nullable = false, unique = true)
    private Turno turno;

    @NotNull
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "medio_pago", nullable = false, length = 20)
    private MedioPago medioPago;

    @Builder.Default
    @Column(name = "emitida_en", nullable = false, updatable = false)
    private LocalDateTime emitidaEn = LocalDateTime.now();
}
