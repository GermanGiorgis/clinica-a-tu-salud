#!/usr/bin/env bash
# Prueba de humo de la aplicación ya levantada (con docker compose o a mano): comprueba que el frontend abre, que el
# backend responde con los datos de demostración y que se puede iniciar sesión con cada cuenta de demostración.
#
#   scripts/smoke-test.sh [URL_DEL_FRONTEND] [URL_DE_LA_API]
#
# Termina con error (código 1) si algo falla, así que sirve también en el CI.
set -u

FRONTEND="${1:-http://localhost:5176}"
API="${2:-http://localhost:8080}"
PASSWORD_DEMO="demo-clinica-2026"
fallos=0

ok()  { echo "  ✔ $1"; }
mal() { echo "  ✘ $1"; fallos=$((fallos + 1)); }

# El backend empieza a responder antes de terminar de cargar los datos de demostración (los carga al arrancar), así que
# cada comprobación se reintenta hasta ~2 minutos antes de darla por fallida.
reintentar() {
  for _ in $(seq 1 40); do
    "$@" > /dev/null 2>&1 && return 0
    sleep 3
  done
  return 1
}

contar()         { curl -fs "$API$1" | grep -o "\"$2\"" | wc -l; }
al_menos()       { [ "$(contar "$1" "$2")" -ge "$3" ]; }
abre_la_app()    { curl -fs "$FRONTEND" | grep -q '<div id="root">'; }
abre_una_ruta()  { [ "$(curl -s -o /dev/null -w '%{http_code}' "$FRONTEND/medicos")" = "200" ]; }
inicia_sesion()  { curl -s -X POST "$API/api/auth/login" -H 'Content-Type: application/json' \
                     -d "{\"email\":\"$1\",\"password\":\"$2\"}" | grep -q '"token"'; }

echo "Frontend ($FRONTEND)"
reintentar abre_la_app   && ok "abre la aplicación"                            || mal "no abre la aplicación"
reintentar abre_una_ruta && ok "las rutas internas (/medicos) también abren"   || mal "las rutas internas no abren"

echo "Backend ($API)"
reintentar al_menos /api/especialidades nombre 30 && ok "$(contar /api/especialidades nombre) especialidades"        || mal "pocas especialidades ($(contar /api/especialidades nombre))"
reintentar al_menos /api/medicos matricula 60     && ok "$(contar /api/medicos matricula) médicos"                    || mal "pocos médicos ($(contar /api/medicos matricula))"
reintentar al_menos /api/patologias especialidades 10 && ok "$(contar /api/patologias especialidades) síntomas con sus especialidades" || mal "pocos síntomas ($(contar /api/patologias especialidades))"

echo "Cuentas de demostración"
for cuenta in "admin@clinica.test:admin1234" \
              "paciente@clinica-demo.test:$PASSWORD_DEMO" \
              "medico@clinica-demo.test:$PASSWORD_DEMO" \
              "recepcion@clinica-demo.test:$PASSWORD_DEMO"; do
  email="${cuenta%%:*}"; clave="${cuenta#*:}"
  reintentar inicia_sesion "$email" "$clave" && ok "inicia sesión: $email" || mal "no inicia sesión: $email"
done

echo
if [ "$fallos" -eq 0 ]; then echo "Todo en orden."; else echo "$fallos comprobación(es) fallaron."; exit 1; fi
