-- Motivos de cancelacion de uso comun. Los eligen el paciente, el medico y el mostrador desde el mismo menu,
-- asi que se redactan en neutro. El admin puede agregar o editar los que necesite.
INSERT INTO motivo_cancelacion (nombre, descripcion) VALUES
    ('El paciente no puede asistir', 'Cancelado a pedido del paciente'),
    ('Cambio de planes del paciente', 'El paciente ya no necesita el turno o resolvió la consulta por otra vía'),
    ('Médico no disponible', 'El médico no puede atender en ese horario'),
    ('Feriado o cierre de la clínica', 'La clínica no atiende ese día'),
    ('Turno cargado por error', 'Se reservó mal el turno: horario, médico o paciente equivocado'),
    ('Otro motivo', 'Cualquier otra razón no listada')
ON CONFLICT (nombre) DO NOTHING;
