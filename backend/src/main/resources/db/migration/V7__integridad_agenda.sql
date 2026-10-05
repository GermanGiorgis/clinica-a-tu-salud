-- Integridad de la agenda garantizada por la base de datos (no solo por el codigo Java):
-- un medico, o un paciente, no puede tener dos turnos activos que se pisen en el tiempo.
-- El indice unico parcial de V1 solo evitaba dos turnos con el MISMO inicio exacto.

-- btree_gist permite combinar "= medico_id" con "solapamiento de rangos" en una restriccion EXCLUDE.
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Control de concurrencia optimista: si dos personas modifican el mismo turno a la vez
-- (por ejemplo, el paciente cancela mientras el medico lo marca ausente), la segunda recibe
-- un conflicto en vez de pisar en silencio el cambio de la primera.
ALTER TABLE turno ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- tsrange(inicio, fin) es semiabierto [inicio, fin): dos turnos consecutivos no se consideran solapados.
ALTER TABLE turno ADD CONSTRAINT ex_turno_medico_sin_solapes
    EXCLUDE USING gist (medico_id WITH =, tsrange(inicio, fin) WITH &&)
    WHERE (estado <> 'CANCELADO');

ALTER TABLE turno ADD CONSTRAINT ex_turno_paciente_sin_solapes
    EXCLUDE USING gist (paciente_id WITH =, tsrange(inicio, fin) WITH &&)
    WHERE (estado <> 'CANCELADO');
