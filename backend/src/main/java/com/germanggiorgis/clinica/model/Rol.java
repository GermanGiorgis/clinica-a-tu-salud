package com.germanggiorgis.clinica.model;

public enum Rol {
    PACIENTE,
    MEDICO,
    /** Mostrador: maneja la agenda y las reservas de todos, pero no ve historias clínicas. */
    RECEPCIONISTA,
    ADMIN
}
