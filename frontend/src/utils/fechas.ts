/** Cuántos días hacia adelante se puede reservar (el backend rechaza más allá: ver VENTANA_RESERVA_DIAS). */
export const VENTANA_RESERVA_DIAS = 60;

/** YYYY-MM-DD en la fecha local (sv-SE es el formato que usan los input type="date"). */
export function hoyISO(): string {
  return new Date().toLocaleDateString("sv-SE");
}

export function ultimoDiaReservableISO(): string {
  const fecha = new Date();
  fecha.setDate(fecha.getDate() + VENTANA_RESERVA_DIAS);
  return fecha.toLocaleDateString("sv-SE");
}

/** "lunes 12 de octubre" a partir de YYYY-MM-DD, sin que la zona horaria lo corra de día. */
export function fechaLegible(iso: string): string {
  return new Date(`${iso}T00:00:00`).toLocaleDateString("es-AR", { weekday: "long", day: "numeric", month: "long" });
}

/** Fecha y hora de un turno como las ve la persona: "lunes, 12 de octubre de 2026, 10:00 a. m.". */
export function fechaHoraLegible(inicio: string): string {
  return new Date(inicio).toLocaleString("es-AR", { dateStyle: "full", timeStyle: "short" });
}
