-- Crea el rol y las bases de datos de DESARROLLO LOCAL.
-- Se ejecuta una sola vez con un superusuario (es re-ejecutable: no falla si ya existen).
--
-- Uso directo:
--   psql -U postgres -h localhost -v app_password=<clave> -f db/init.sql
-- O con el script auxiliar (lee la clave de application-local.properties):
--   powershell -ExecutionPolicy Bypass -File backend\db\crear-db-local.ps1

SELECT format('CREATE ROLE clinica LOGIN PASSWORD %L', :'app_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'clinica')
\gexec

SELECT 'CREATE DATABASE clinica OWNER clinica ENCODING ''UTF8'''
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'clinica')
\gexec

-- Base separada para los tests de integracion.
SELECT 'CREATE DATABASE clinica_test OWNER clinica ENCODING ''UTF8'''
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'clinica_test')
\gexec
