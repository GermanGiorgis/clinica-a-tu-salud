-- Catalogo inicial de especialidades de una clinica. El admin puede seguir agregando, editando
-- o borrando (si no hay medicos ni patologias asociadas) desde la API de catalogos.
--
-- Primero se corrigen las que se cargaron a mano sin tildes durante el desarrollo, para que el
-- INSERT de abajo no las duplique con otro nombre.
UPDATE especialidad SET nombre = 'Clínica Médica'
 WHERE nombre = 'Clinica Medica' AND NOT EXISTS (SELECT 1 FROM especialidad WHERE nombre = 'Clínica Médica');
UPDATE especialidad SET nombre = 'Dermatología'
 WHERE nombre = 'Dermatologia' AND NOT EXISTS (SELECT 1 FROM especialidad WHERE nombre = 'Dermatología');

INSERT INTO especialidad (nombre, descripcion) VALUES
    ('Alergia e Inmunología', 'Alergias, asma alérgica y trastornos del sistema inmune'),
    ('Cardiología', 'Corazón, presión arterial y circulación'),
    ('Cirugía General', 'Intervenciones quirúrgicas del abdomen, hernias y partes blandas'),
    ('Cirugía Plástica y Reparadora', 'Reconstrucción, quemaduras y cirugía estética'),
    ('Cirugía Vascular', 'Várices, arterias y venas'),
    ('Clínica Médica', 'Atención general del adulto y primer contacto'),
    ('Dermatología', 'Piel, pelo y uñas'),
    ('Diagnóstico por Imágenes', 'Radiografías, ecografías y tomografías'),
    ('Endocrinología', 'Diabetes, tiroides y trastornos hormonales'),
    ('Fonoaudiología', 'Voz, lenguaje, audición y deglución'),
    ('Gastroenterología', 'Estómago, intestino, hígado y vías biliares'),
    ('Genética Médica', 'Enfermedades hereditarias y asesoramiento genético'),
    ('Geriatría', 'Salud y cuidado de las personas mayores'),
    ('Ginecología', 'Salud de la mujer y del aparato reproductor'),
    ('Hematología', 'Enfermedades de la sangre'),
    ('Infectología', 'Infecciones, vacunas y enfermedades transmisibles'),
    ('Kinesiología y Fisiatría', 'Rehabilitación física y recuperación del movimiento'),
    ('Medicina del Deporte', 'Evaluación y cuidado de quienes hacen actividad física'),
    ('Medicina Familiar', 'Atención integral de toda la familia'),
    ('Nefrología', 'Riñones y control de la función renal'),
    ('Neonatología', 'Recién nacidos'),
    ('Neumonología', 'Pulmones y vías respiratorias'),
    ('Neurología', 'Cerebro, nervios, migrañas y epilepsia'),
    ('Nutrición', 'Alimentación, control de peso y planes nutricionales'),
    ('Obstetricia', 'Embarazo, parto y posparto'),
    ('Odontología', 'Dientes, encías y salud bucal'),
    ('Oftalmología', 'Ojos y visión'),
    ('Oncología', 'Diagnóstico y tratamiento del cáncer'),
    ('Otorrinolaringología', 'Oído, nariz y garganta'),
    ('Pediatría', 'Salud de niños y adolescentes'),
    ('Proctología', 'Colon, recto y ano'),
    ('Psicología', 'Salud emocional y psicoterapia'),
    ('Psiquiatría', 'Trastornos de salud mental y tratamiento farmacológico'),
    ('Reumatología', 'Articulaciones, huesos y enfermedades autoinmunes'),
    ('Traumatología y Ortopedia', 'Huesos, articulaciones, músculos y lesiones'),
    ('Urología', 'Vías urinarias y aparato reproductor masculino')
ON CONFLICT (nombre) DO NOTHING;
