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
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "turno")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Turno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medico_id", nullable = false)
    private Medico medico;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consultorio_id", nullable = false)
    private Consultorio consultorio;

    /** Usuario que registró el turno: el propio paciente, su médico o un admin. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creado_por_id", nullable = false)
    private Usuario creadoPor;

    /** Solo se completa cuando estado = CANCELADO (ver constraint en la migración). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "motivo_cancelacion_id")
    private MotivoCancelacion motivoCancelacion;

    /** Opcional: con qué cobertura del paciente se atiende este turno. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "afiliacion_id")
    private Afiliacion afiliacion;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime inicio;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime fin;

    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private EstadoTurno estado = EstadoTurno.RESERVADO;

    @Column(name = "motivo_consulta", length = 500)
    private String motivoConsulta;

    @Builder.Default
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();

    /**
     * Control de concurrencia optimista: si dos transacciones modifican el mismo turno a la vez,
     * la segunda falla en vez de pisar el cambio de la primera (ver V7).
     */
    @Version
    private Long version;
}
