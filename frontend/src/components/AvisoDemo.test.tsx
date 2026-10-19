import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AvisoDemo } from "./AvisoDemo";

describe("AvisoDemo", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it("en la versión normal no muestra nada", () => {
    vi.stubEnv("VITE_MODO_DEMO", "false");

    const { container } = render(<AvisoDemo />);

    expect(container).toBeEmptyDOMElement();
  });

  it("en la versión de demostración avisa que los datos se borran y que no hay que cargar datos reales", () => {
    vi.stubEnv("VITE_MODO_DEMO", "true");

    render(<AvisoDemo />);

    expect(screen.getByRole("note")).toHaveTextContent("Versión de demostración");
    expect(screen.getByRole("note")).toHaveTextContent("No ingreses datos personales reales");
  });
});
