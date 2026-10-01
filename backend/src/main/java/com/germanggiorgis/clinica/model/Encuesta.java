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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 1:1 con Turno; el paciente la carga una sola vez, después de ser atendido. */
@Entity
@Table(name = "encuesta")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Encuesta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "turno_id", nullable = false, unique = true)
    private Turno turno;

    @NotNull
    @Min(1)
    @Max(5)
    @Column(nullable = false)
    private Integer puntaje;

    @Column(length = 500)
    private String comentario;

    @Builder.Default
    @Column(name = "creada_en", nullable = false, updatable = false)
    private LocalDateTime creadaEn = LocalDateTime.now();
}
