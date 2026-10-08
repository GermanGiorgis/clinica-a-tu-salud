/** Deja solo los números y corta en 9: quien pega "30.111.222" o "30 111 222" manda igual un DNI válido. */
export function soloDigitos(texto: string): string {
  return texto.replace(/\D/g, "").slice(0, 9);
}
