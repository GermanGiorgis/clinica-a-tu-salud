import { hoyISO } from "./fechas";

export interface DatosRegistro {
  nombre: string;
  apellido: string;
  email: string;
  password: string;
  telefono: string;
  dni: string;
  fechaNacimiento: string;
}

export type CampoRegistro = keyof DatosRegistro;
export type ErroresRegistro = Partial<Record<CampoRegistro, string>>;

/** Orden en que aparecen en pantalla: el primero con error es el que recibe el foco. */
export const CAMPOS_REGISTRO: CampoRegistro[] = ["nombre", "apellido", "email", "password", "telefono", "dni", "fechaNacimiento"];

// Las mismas reglas que el servidor (RegistroPacienteRequest): si cambian allá, cambian acá.
const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s.]{2,}$/;
const LARGO_MAXIMO_NOMBRE = 100;
const MINIMO_CLAVE = 10;
const MAXIMO_BYTES_CLAVE = 72;
const PRIMER_ANIO = "1900-01-01";

function validarNombre(valor: string, que: string): string | undefined {
  const texto = valor.trim();
  if (!texto) return `Escribí tu ${que}.`;
  if (texto.length > LARGO_MAXIMO_NOMBRE) return `Es demasiado largo: el máximo son ${LARGO_MAXIMO_NOMBRE} caracteres.`;
  return undefined;
}

function validarEmail(valor: string): string | undefined {
  const texto = valor.trim();
  if (!texto) return "Escribí tu email.";
  if (/\s/.test(texto)) return "El email no puede tener espacios.";
  if (!texto.includes("@")) return "Al email le falta el @. Tiene que ser como nombre@dominio.com.";
  if (EMAIL.test(texto)) return undefined;
  const dominio = texto.slice(texto.indexOf("@") + 1);
  if (!texto.slice(0, texto.indexOf("@"))) return "Al email le falta lo que va antes del @. Tiene que ser como nombre@dominio.com.";
  if (!dominio.includes(".")) return "Al email le falta la terminación del dominio (por ejemplo .com): nombre@dominio.com.";
  return "El email no es válido. Tiene que ser como nombre@dominio.com.";
}

function validarClave(valor: string): string | undefined {
  if (!valor) return "Elegí una contraseña.";
  if (valor.length < MINIMO_CLAVE) {
    return `Es muy corta: tiene ${valor.length} caracteres y necesita al menos ${MINIMO_CLAVE}.`;
  }
  if (new TextEncoder().encode(valor).length > MAXIMO_BYTES_CLAVE) {
    return `Es demasiado larga: el máximo son ${MAXIMO_BYTES_CLAVE} caracteres (menos si usa tildes o la ñ).`;
  }
  return undefined;
}

function validarTelefono(valor: string): string | undefined {
  const texto = valor.trim();
  if (!texto) return undefined; // opcional
  if (!/^[0-9+()\s-]+$/.test(texto)) return "Solo puede tener números, espacios, guiones, paréntesis y +. Sin letras.";
  const digitos = texto.replace(/\D/g, "").length;
  if (digitos < 8 || digitos > 15) {
    return `Tiene que tener entre 8 y 15 números (escribiste ${digitos}). Ejemplo: 261 342-9556.`;
  }
  return undefined;
}

function validarDni(valor: string): string | undefined {
  if (!valor) return "Escribí tu DNI, solo números.";
  if (valor.length < 6 || valor.length > 9) {
    return `El DNI tiene entre 6 y 9 números y escribiste ${valor.length}. Sin puntos ni espacios.`;
  }
  return undefined;
}

/** YYYY-MM-DD de un día que existe: Date.parse acepta "1998-02-31" y lo corre a marzo, así que se compara de ida y vuelta. */
function existeEnElCalendario(valor: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(valor)) return false;
  const [anio, mes, dia] = valor.split("-").map(Number);
  const fecha = new Date(Date.UTC(anio, mes - 1, dia));
  return fecha.getUTCFullYear() === anio && fecha.getUTCMonth() === mes - 1 && fecha.getUTCDate() === dia;
}

function validarNacimiento(valor: string): string | undefined {
  if (!valor) return "Elegí tu fecha de nacimiento (día, mes y año completos).";
  if (!existeEnElCalendario(valor)) return "La fecha no es válida.";
  if (valor < PRIMER_ANIO) return "Revisá el año: no puede ser anterior a 1900.";
  if (valor >= hoyISO()) return "La fecha de nacimiento tiene que ser anterior a hoy.";
  return undefined;
}

/** Valida un solo campo; devuelve el mensaje para quien lo llena, o undefined si está bien. */
export function validarCampoRegistro(campo: CampoRegistro, datos: DatosRegistro): string | undefined {
  switch (campo) {
    case "nombre": return validarNombre(datos.nombre, "nombre");
    case "apellido": return validarNombre(datos.apellido, "apellido");
    case "email": return validarEmail(datos.email);
    case "password": return validarClave(datos.password);
    case "telefono": return validarTelefono(datos.telefono);
    case "dni": return validarDni(datos.dni);
    case "fechaNacimiento": return validarNacimiento(datos.fechaNacimiento);
  }
}

/** Valida todo el formulario junto, para marcar de una vez todo lo que falta o está mal. */
export function validarRegistro(datos: DatosRegistro): ErroresRegistro {
  const errores: ErroresRegistro = {};
  for (const campo of CAMPOS_REGISTRO) {
    const mensaje = validarCampoRegistro(campo, datos);
    if (mensaje) errores[campo] = mensaje;
  }
  return errores;
}
