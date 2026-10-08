import { Baby, Bone, Heartbeat, Scissors, Stethoscope } from "@phosphor-icons/react";
import { describe, expect, it } from "vitest";
import { iconoDeEspecialidad, normalizar } from "./especialidades";

describe("normalizar", () => {
  it("quita tildes, pasa a minúsculas y recorta espacios", () => {
    expect(normalizar("  Traumatología y Ortopedia ")).toBe("traumatologia y ortopedia");
    expect(normalizar("OTORRINOLARINGOLOGÍA")).toBe("otorrinolaringologia");
  });
});

describe("iconoDeEspecialidad", () => {
  it("elige el ícono por el nombre, con o sin tildes", () => {
    expect(iconoDeEspecialidad("Cardiología")).toBe(Heartbeat);
    expect(iconoDeEspecialidad("cardiologia")).toBe(Heartbeat);
    expect(iconoDeEspecialidad("Traumatología y Ortopedia")).toBe(Bone);
    expect(iconoDeEspecialidad("Pediatría")).toBe(Baby);
    expect(iconoDeEspecialidad("Cirugía Plástica y Reparadora")).toBe(Scissors);
  });

  it("una especialidad que no conoce, o sin nombre, usa el estetoscopio", () => {
    expect(iconoDeEspecialidad("Medicina Estética Avanzada")).toBe(Stethoscope);
    expect(iconoDeEspecialidad("")).toBe(Stethoscope);
  });
});
