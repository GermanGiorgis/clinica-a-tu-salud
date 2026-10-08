import {
  Baby, Barbell, Bone, Brain, ChatsCircle, Dna, Drop, Ear, Eye, FirstAid, Flask, FlowerLotus, Hand, Heartbeat,
  Pill, Scan, Scissors, ShieldPlus, Smiley, Sparkle, Stethoscope, Tooth, Users, Virus, Waveform, Wheelchair,
  type Icon,
} from "@phosphor-icons/react";

/** Minúsculas y sin tildes, para buscar y comparar nombres sin que importe cómo se escribieron. */
export function normalizar(texto: string): string {
  return texto.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase().trim();
}

/** Se busca por fragmentos del nombre (sin tildes); gana la primera coincidencia. */
const ICONOS: [string, Icon][] = [
  ["cardiolog", Heartbeat],
  ["neurolog", Brain],
  ["neonatolog", Baby],
  ["pediatr", Baby],
  ["obstetric", Baby],
  ["oftalmolog", Eye],
  ["odontolog", Tooth],
  ["traumatolog", Bone],
  ["reumatolog", Bone],
  ["otorrino", Ear],
  ["dermatolog", Hand],
  ["ginecolog", FlowerLotus],
  ["urolog", Drop],
  ["nefrolog", Drop],
  ["hematolog", Drop],
  ["neumonolog", Waveform],
  ["fonoaudiolog", Waveform],
  ["infectolog", Virus],
  ["alergia", Sparkle],
  ["endocrinolog", Flask],
  ["gastroenterolog", Pill],
  ["proctolog", FirstAid],
  ["nutricion", Smiley],
  ["oncolog", ShieldPlus],
  ["psiquiatr", ChatsCircle],
  ["psicolog", ChatsCircle],
  ["genetic", Dna],
  ["imagenes", Scan],
  ["cirugia", Scissors],
  ["kinesiolog", Wheelchair],
  ["deporte", Barbell],
  ["geriatr", Users],
];

export function iconoDeEspecialidad(nombre: string): Icon {
  const n = normalizar(nombre);
  return ICONOS.find(([clave]) => n.includes(clave))?.[1] ?? Stethoscope;
}
