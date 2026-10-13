package com.germanggiorgis.clinica.validation;

/** Formatos de datos de contacto compartidos por los formularios de alta (registro y alta por el administrador). */
public final class Formatos {

    /** Algo@dominio.ext: exige el punto y una terminación, que @Email deja pasar ("nombre@gmail" es válido para él). */
    public static final String EMAIL = "^[^@\\s]+@[^@\\s]+\\.[^@\\s.]{2,}$";
    public static final String MENSAJE_EMAIL = "tiene que ser un email completo, por ejemplo nombre@dominio.com";

    /** Vacío, o entre 8 y 15 dígitos con +, espacios, guiones y paréntesis como separadores. */
    public static final String TELEFONO = "^$|^(?=(?:\\D*\\d){8,15}\\D*$)[0-9+()\\s-]+$";
    public static final String MENSAJE_TELEFONO = "tiene que tener entre 8 y 15 números (podés usar espacios, guiones, paréntesis y + al inicio)";

    private Formatos() {
    }
}
