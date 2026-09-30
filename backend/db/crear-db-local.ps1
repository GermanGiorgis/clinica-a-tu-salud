# Crea el rol y las bases de desarrollo local (una sola vez).
# Uso:
#   powershell -ExecutionPolicy Bypass -File backend\db\crear-db-local.ps1
# psql va a pedir la contrasena del superusuario "postgres".

$ErrorActionPreference = 'Stop'

$props = Join-Path $PSScriptRoot '..\src\main\resources\application-local.properties'
if (-not (Test-Path $props)) {
    throw "Falta $props. Copia application-local.properties.example y completa la clave."
}
$line = Get-Content $props | Where-Object { $_ -match '^spring\.datasource\.password=' } | Select-Object -First 1
$pw = $line -replace '^spring\.datasource\.password=', ''
if (-not $pw) { throw 'spring.datasource.password esta vacia en application-local.properties.' }

$psql = (Get-Command psql -ErrorAction SilentlyContinue).Source
if (-not $psql) { $psql = 'C:\Program Files\PostgreSQL\18\bin\psql.exe' }

& $psql -U postgres -h localhost -v ON_ERROR_STOP=1 -v "app_password=$pw" -f (Join-Path $PSScriptRoot 'init.sql')
if ($LASTEXITCODE -ne 0) { throw "psql termino con error ($LASTEXITCODE)." }
Write-Host 'Listo: rol "clinica" y bases "clinica" / "clinica_test" disponibles.'
