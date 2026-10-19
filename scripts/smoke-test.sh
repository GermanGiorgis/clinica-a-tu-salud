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

ok()   { echo "  ✔ $1"; }
mal()  { echo "  ✘ $1"; fallos=$((fallos + 1)); }

echo "Esperando a que el backend responda (puede tardar un minuto)..."
for _ in $(seq 1 60); do
  curl -fs "$API/api/especialidades" > /dev/null 2>&1 && break
  sleep 3
done

echo "Frontend ($FRONTEND)"
if curl -fs "$FRONTEND" | grep -q "<div id=\"root\">"; then ok "abre la aplicación"; else mal "no abre la aplicación"; fi
if [ "$(curl -s -o /dev/null -w '%{http_code}' "$FRONTEND/medicos")" = "200" ]; then ok "las rutas internas (/medicos) también abren"; else mal "las rutas internas no abren"; fi

echo "Backend ($API)"
especialidades=$(curl -fs "$API/api/especialidades" | grep -o '"nombre"' | wc -l)
[ "$especialidades" -ge 30 ] && ok "$especialidades especialidades" || mal "pocas especialidades ($especialidades)"
medicos=$(curl -fs "$API/api/medicos" | grep -o '"matricula"' | wc -l)
[ "$medicos" -ge 60 ] && ok "$medicos médicos" || mal "pocos médicos ($medicos)"
sintomas=$(curl -fs "$API/api/patologias" | grep -o '"especialidades"' | wc -l)
[ "$sintomas" -ge 10 ] && ok "$sintomas síntomas con sus especialidades" || mal "pocos síntomas ($sintomas)"

echo "Cuentas de demostración"
for cuenta in "admin@clinica.test:admin1234" \
              "paciente@clinica-demo.test:$PASSWORD_DEMO" \
              "medico@clinica-demo.test:$PASSWORD_DEMO" \
              "recepcion@clinica-demo.test:$PASSWORD_DEMO"; do
  email="${cuenta%%:*}"; clave="${cuenta#*:}"
  respuesta=$(curl -s -X POST "$API/api/auth/login" -H 'Content-Type: application/json' \
              -d "{\"email\":\"$email\",\"password\":\"$clave\"}")
  if echo "$respuesta" | grep -q '"token"'; then ok "inicia sesión: $email"; else mal "no inicia sesión: $email"; fi
done

echo
if [ "$fallos" -eq 0 ]; then echo "Todo en orden."; else echo "$fallos comprobación(es) fallaron."; exit 1; fi
