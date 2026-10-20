#!/usr/bin/env node
// Saca las capturas del README (docs/capturas/*.jpg) de la aplicación ya levantada, con un Chrome sin ventana que
// se maneja por el protocolo DevTools (no hace falta instalar nada: solo Node 22+ y Chrome o Edge).
//
//   node scripts/capturar-pantallas.mjs [URL_DEL_FRONTEND] [URL_DE_LA_API]
//
// Necesita la aplicación con los datos de demostración (docker compose up, o el backend con SEED_DEMO_* y
// `npm run dev`). Inicia sesión con las cuentas de demostración, así que no toca datos reales. Conviene una base
// limpia (con docker compose la hay): en una base de pruebas sucia, las listas muestran esos datos.
import { spawn } from "node:child_process";
import { existsSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const FRONTEND = process.argv[2] ?? "http://localhost:5176";
const API = process.argv[3] ?? "http://localhost:8080";
const SALIDA = join(dirname(fileURLToPath(import.meta.url)), "..", "docs", "capturas");
const PUERTO = 9333;
const CLAVE_DEMO = "demo-clinica-2026";

const NAVEGADORES = [
  process.env.CHROME_PATH,
  "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
  "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
  "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
  "/usr/bin/google-chrome",
  "/usr/bin/chromium",
].filter(Boolean);

const navegador = NAVEGADORES.find((ruta) => existsSync(ruta));
if (!navegador) {
  console.error("No encontré Chrome ni Edge. Indicá la ruta con CHROME_PATH=...");
  process.exit(1);
}

const perfil = mkdtempSync(join(tmpdir(), "clinica-capturas-"));
const proceso = spawn(navegador, [
  "--headless=new", `--remote-debugging-port=${PUERTO}`, `--user-data-dir=${perfil}`,
  "--hide-scrollbars", "--no-first-run", "--disable-gpu", "about:blank",
], { stdio: "ignore" });

const dormir = (ms) => new Promise((r) => setTimeout(r, ms));

async function conectar() {
  for (let i = 0; i < 50; i++) {
    try {
      const paginas = await (await fetch(`http://127.0.0.1:${PUERTO}/json`)).json();
      const pagina = paginas.find((p) => p.type === "page");
      if (pagina) return pagina.webSocketDebuggerUrl;
    } catch { /* el navegador todavía está arrancando */ }
    await dormir(200);
  }
  throw new Error("El navegador no arrancó");
}

const ws = new WebSocket(await conectar());
await new Promise((r) => ws.addEventListener("open", r, { once: true }));
let ids = 0;
const pendientes = new Map();
ws.addEventListener("message", ({ data }) => {
  const m = JSON.parse(data);
  if (m.id && pendientes.has(m.id)) {
    const { ok, mal } = pendientes.get(m.id);
    pendientes.delete(m.id);
    m.error ? mal(new Error(m.error.message)) : ok(m.result);
  }
});
const cdp = (method, params = {}) => new Promise((ok, mal) => {
  const id = ++ids;
  pendientes.set(id, { ok, mal });
  ws.send(JSON.stringify({ id, method, params }));
});

const evaluar = async (expression) => {
  const r = await cdp("Runtime.evaluate", { expression, awaitPromise: true, returnByValue: true });
  if (r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description ?? "falló el script de la página");
  return r.result.value;
};

async function esperar(condicion, descripcion, ms = 15000) {
  const limite = Date.now() + ms;
  while (Date.now() < limite) {
    if (await evaluar(`Boolean(${condicion})`)) return;
    await dormir(150);
  }
  throw new Error(`Se agotó la espera: ${descripcion}`);
}

const viewport = (width, height, mobile = false) =>
  cdp("Emulation.setDeviceMetricsOverride", { width, height, deviceScaleFactor: mobile ? 2 : 1, mobile });

async function ir(ruta, listoCuando) {
  await cdp("Page.navigate", { url: `${FRONTEND}${ruta}` });
  await esperar("document.readyState === 'complete'", `cargar ${ruta}`);
  if (listoCuando) await esperar(listoCuando, `ver ${ruta}`);
  await dormir(300);
}

async function iniciarSesion(email, password) {
  await evaluar(`(async () => {
    const r = await fetch(${JSON.stringify(API)} + '/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email: ${JSON.stringify(email)}, password: ${JSON.stringify(password)} }) });
    if (!r.ok) throw new Error('No se pudo iniciar sesión con ' + ${JSON.stringify(email)});
    const j = await r.json();
    localStorage.setItem('token', j.token);
    localStorage.setItem('usuario', JSON.stringify({ email: j.email, nombre: j.nombre, apellido: j.apellido, rol: j.rol }));
  })()`);
}

const cerrarSesion = () => evaluar("localStorage.clear()");

/** Saca la captura con el alto que tenga la página (entre 600 y 1400 px), para que no quede cortada ni con vacío. */
async function capturar(nombre, ancho = 1280, mobile = false) {
  await viewport(ancho, 800, mobile);
  await dormir(250);
  const alto = await evaluar("Math.ceil(document.documentElement.scrollHeight)");
  await viewport(ancho, Math.min(Math.max(alto, 600), 1400), mobile);
  await dormir(300);
  // JPEG: la foto de fondo pesa mucho en PNG (medio mega por captura) y casi nada en JPEG.
  const { data } = await cdp("Page.captureScreenshot", { format: "jpeg", quality: 85 });
  mkdirSync(SALIDA, { recursive: true });
  writeFileSync(join(SALIDA, `${nombre}.jpg`), Buffer.from(data, "base64"));
  console.log(`  ✔ docs/capturas/${nombre}.jpg`);
}

const json = async (ruta) => (await fetch(`${API}${ruta}`)).json();

try {
  await cdp("Page.enable");
  await viewport(1280, 800);
  await ir("/"); // origen de la aplicación, para poder guardar la sesión

  const sintomas = await json("/api/patologias");
  const dolorDeCabeza = sintomas.find((s) => s.nombre === "Dolor de cabeza");
  const medicos = await json("/api/medicos");
  const medicoDemo = medicos.find((m) => m.matricula === "DEMO-LOGIN");
  if (!dolorDeCabeza || !medicoDemo) throw new Error("Faltan los datos de demostración (¿SEED_DEMO_CUENTAS y SEED_DEMO_MEDICOS?)");

  console.log("Sin sesión");
  await cerrarSesion();
  await ir("/", "document.querySelector('.home__especialidades')");
  await capturar("01-portada");
  await ir(`/medicos?sintoma=${dolorDeCabeza.id}`, "document.querySelector('[role=status]')");
  await capturar("02-sintomas");

  console.log("Paciente");
  await iniciarSesion("paciente@clinica-demo.test", CLAVE_DEMO);
  await ir(`/medicos/${medicoDemo.id}`, "document.querySelector('.medico-detalle__horarios button') || [...document.querySelectorAll('button')].some(b => b.textContent.startsWith('Ver el próximo día'))");
  await evaluar("(() => { const b = [...document.querySelectorAll('button')].find(b => b.textContent.startsWith('Ver el próximo día')); if (b) b.click(); })()");
  await esperar("document.querySelector('.medico-detalle__horarios button')", "ver horarios libres");
  await evaluar("document.querySelectorAll('.medico-detalle__horarios button')[2].click()");
  await esperar("document.querySelector('.medico-detalle__reserva')", "abrir el formulario de reserva");
  await capturar("03-reserva");
  await ir("/mis-turnos", "document.querySelector('main li, main .card')");
  await capturar("04-mis-turnos");

  console.log("Médico");
  await cerrarSesion();
  await iniciarSesion("medico@clinica-demo.test", CLAVE_DEMO);
  await ir("/agenda", "document.querySelector('main li, main .card')");
  await capturar("05-agenda-medico");

  console.log("Administrador");
  await cerrarSesion();
  await iniciarSesion("admin@clinica.test", "admin1234");
  await ir("/usuarios", "document.querySelector('main table, main li')");
  await capturar("06-usuarios-admin");

  console.log("Celular");
  await cerrarSesion();
  await ir("/", "document.querySelector('.home__especialidades')");
  await capturar("07-movil", 390, true);
} finally {
  ws.close();
  proceso.kill();
  await dormir(500);
  rmSync(perfil, { recursive: true, force: true });
}
