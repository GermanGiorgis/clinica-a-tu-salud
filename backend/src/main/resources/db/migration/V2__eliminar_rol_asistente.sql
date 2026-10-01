-- El rol ASISTENTE se elimina: en la practica hacia lo mismo que ADMIN
-- (ver docs/modelo-datos.md). Si alguna cuenta de prueba quedo con ese rol,
-- se reasigna a ADMIN antes de restringir el CHECK.
UPDATE usuario SET rol = 'ADMIN' WHERE rol = 'ASISTENTE';

ALTER TABLE usuario DROP CONSTRAINT ck_usuario_rol;
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_rol CHECK (rol IN ('PACIENTE', 'MEDICO', 'ADMIN'));
