/**
 * Modo demostración: se activa al compilar con VITE_MODO_DEMO=true (docker compose y la demo en línea lo hacen).
 * Muestra un aviso en todas las pantallas y, en el login, las cuentas de prueba. Se lee al usarlo (no al importar)
 * para poder probarlo.
 */
export function modoDemo(): boolean {
  return import.meta.env.VITE_MODO_DEMO === "true";
}

export interface CuentaDemo {
  rol: string;
  email: string;
  password: string;
  detalle: string;
}

/** Las mismas que crean AdminSeeder y CuentasDeDemostracion en el backend. */
export const CUENTAS_DEMO: CuentaDemo[] = [
  { rol: "Paciente", email: "paciente@clinica-demo.test", password: "demo-clinica-2026", detalle: "reserva turnos y ve su historial" },
  { rol: "Médico", email: "medico@clinica-demo.test", password: "demo-clinica-2026", detalle: "atiende su agenda y carga consultas" },
  { rol: "Recepción", email: "recepcion@clinica-demo.test", password: "demo-clinica-2026", detalle: "atiende el mostrador y carga feriados" },
  { rol: "Administrador", email: "admin@clinica.test", password: "admin1234", detalle: "usuarios, métricas y auditoría" },
];
