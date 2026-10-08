import type { Rol } from "../types";

/** Admin y recepción atienden el mostrador: ven la agenda de todos y reservan a nombre de un paciente por DNI. */
export function esPersonalDeMostrador(rol: Rol | undefined): boolean {
  return rol === "ADMIN" || rol === "RECEPCIONISTA";
}

/** Quien puede reservar a nombre de otro paciente (indicando su DNI): el médico y el mostrador. */
export function esPersonalDeLaClinica(rol: Rol | undefined): boolean {
  return rol === "MEDICO" || esPersonalDeMostrador(rol);
}

/** Minutos que se espera a un paciente antes de poder darlo por ausente (tiene que coincidir con el backend). */
export const TOLERANCIA_AUSENCIA_MINUTOS = 20;

/** ¿Ya pasó la tolerancia desde la hora del turno? `inicio` viene como fecha-hora local de la clínica. */
export function pasoLaTolerancia(inicio: string, ahora: number = Date.now()): boolean {
  return ahora >= new Date(inicio).getTime() + TOLERANCIA_AUSENCIA_MINUTOS * 60_000;
}
