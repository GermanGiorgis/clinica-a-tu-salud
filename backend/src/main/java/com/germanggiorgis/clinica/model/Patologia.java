package com.germanggiorgis.clinica.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Catálogo de síntomas/patologías que el admin mapea a una o más Especialidades, para
 * que un paciente que no sabe a qué especialista ir pueda elegir "qué le
 * pasa" y el sistema le muestre las especialidades que corresponden.
 */
@Entity
@Table(name = "patologia")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Patologia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String nombre;

    /** Las especialidades que atienden este síntoma; ninguna sola alcanza ("dolor de cabeza": neurología o clínica). */
    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "patologia_especialidad",
            joinColumns = @JoinColumn(name = "patologia_id"),
            inverseJoinColumns = @JoinColumn(name = "especialidad_id")
    )
    private List<Especialidad> especialidades = new ArrayList<>();
}
