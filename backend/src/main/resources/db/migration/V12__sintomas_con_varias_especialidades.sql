-- Un sintoma suele tener mas de una especialidad que lo atiende ("dolor de cabeza": neurologia o clinica
-- medica), asi que patologia deja de apuntar a UNA especialidad y pasa a una tabla intermedia.
CREATE TABLE patologia_especialidad (
    patologia_id    BIGINT NOT NULL REFERENCES patologia (id) ON DELETE CASCADE,
    especialidad_id BIGINT NOT NULL REFERENCES especialidad (id),
    PRIMARY KEY (patologia_id, especialidad_id)
);
CREATE INDEX ix_patologia_especialidad_especialidad ON patologia_especialidad (especialidad_id);

-- Lo ya cargado conserva su especialidad.
INSERT INTO patologia_especialidad (patologia_id, especialidad_id)
SELECT id, especialidad_id FROM patologia;

DROP INDEX ix_patologia_especialidad;
ALTER TABLE patologia DROP COLUMN especialidad_id;

-- Catalogo inicial de sintomas basicos, para que quien no sabe a que especialista ir elija "que le pasa"
-- y vea las especialidades que corresponden. Si un nombre ya existe (cargado a mano) se deja como esta.
WITH mapa (sintoma, especialidad) AS (VALUES
    ('Dolor de cabeza', 'Neurología'),
    ('Dolor de cabeza', 'Clínica Médica'),
    ('Mareos o desmayos', 'Clínica Médica'),
    ('Mareos o desmayos', 'Neurología'),
    ('Mareos o desmayos', 'Otorrinolaringología'),
    ('Hormigueo, temblores o pérdida de fuerza', 'Neurología'),
    ('Dolor de estómago o de panza', 'Gastroenterología'),
    ('Dolor de estómago o de panza', 'Clínica Médica'),
    ('Acidez, reflujo o digestión pesada', 'Gastroenterología'),
    ('Náuseas, vómitos o diarrea', 'Gastroenterología'),
    ('Náuseas, vómitos o diarrea', 'Clínica Médica'),
    ('Hemorroides o dolor al evacuar', 'Proctología'),
    ('Hemorroides o dolor al evacuar', 'Gastroenterología'),
    ('Lunares o manchas en la piel', 'Dermatología'),
    ('Granitos, acné o picazón en la piel', 'Dermatología'),
    ('Granitos, acné o picazón en la piel', 'Alergia e Inmunología'),
    ('Caída del pelo o problemas en las uñas', 'Dermatología'),
    ('Dolor de pecho o palpitaciones', 'Cardiología'),
    ('Dolor de pecho o palpitaciones', 'Clínica Médica'),
    ('Presión alta o colesterol', 'Cardiología'),
    ('Presión alta o colesterol', 'Clínica Médica'),
    ('Várices o piernas hinchadas', 'Cirugía Vascular'),
    ('Várices o piernas hinchadas', 'Clínica Médica'),
    ('Tos, falta de aire o silbidos en el pecho', 'Neumonología'),
    ('Tos, falta de aire o silbidos en el pecho', 'Clínica Médica'),
    ('Dolor de garganta o de oído', 'Otorrinolaringología'),
    ('Dolor de garganta o de oído', 'Clínica Médica'),
    ('Nariz tapada, sinusitis o alergia', 'Otorrinolaringología'),
    ('Nariz tapada, sinusitis o alergia', 'Alergia e Inmunología'),
    ('Fiebre o malestar general', 'Clínica Médica'),
    ('Fiebre o malestar general', 'Infectología'),
    ('Fiebre o malestar general', 'Pediatría'),
    ('Dolor de espalda o de cintura', 'Traumatología y Ortopedia'),
    ('Dolor de espalda o de cintura', 'Kinesiología y Fisiatría'),
    ('Dolor de rodilla, hombro u otra articulación', 'Traumatología y Ortopedia'),
    ('Dolor de rodilla, hombro u otra articulación', 'Reumatología'),
    ('Dolor de rodilla, hombro u otra articulación', 'Kinesiología y Fisiatría'),
    ('Golpe, torcedura o fractura', 'Traumatología y Ortopedia'),
    ('Visión borrosa u ojos rojos', 'Oftalmología'),
    ('Dolor de muelas o de encías', 'Odontología'),
    ('Ardor al orinar o infección urinaria', 'Urología'),
    ('Ardor al orinar o infección urinaria', 'Clínica Médica'),
    ('Ardor al orinar o infección urinaria', 'Ginecología'),
    ('Dolor menstrual o sangrado irregular', 'Ginecología'),
    ('Embarazo o control del embarazo', 'Obstetricia'),
    ('Embarazo o control del embarazo', 'Ginecología'),
    ('Control de un bebé o un niño', 'Pediatría'),
    ('Tristeza, ansiedad o insomnio', 'Psicología'),
    ('Tristeza, ansiedad o insomnio', 'Psiquiatría'),
    ('Cansancio constante o cambios de peso', 'Endocrinología'),
    ('Cansancio constante o cambios de peso', 'Clínica Médica'),
    ('Cansancio constante o cambios de peso', 'Nutrición'),
    ('Bajar de peso o mejorar la alimentación', 'Nutrición'),
    ('Bajar de peso o mejorar la alimentación', 'Endocrinología'),
    ('Chequeo médico general', 'Clínica Médica'),
    ('Chequeo médico general', 'Medicina Familiar')
),
nuevas AS (
    INSERT INTO patologia (nombre)
    SELECT DISTINCT sintoma FROM mapa
    ON CONFLICT (lower(nombre)) DO NOTHING
    RETURNING id, nombre
)
INSERT INTO patologia_especialidad (patologia_id, especialidad_id)
SELECT n.id, e.id
  FROM nuevas n
  JOIN mapa m ON m.sintoma = n.nombre
  JOIN especialidad e ON e.nombre = m.especialidad;
