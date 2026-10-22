import { act, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import * as clienteApi from "../api/client";
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

  describe("cuando el servidor tarda", () => {
    it("explica que se está despertando mientras hay un pedido lento y lo retira después", () => {
      vi.stubEnv("VITE_MODO_DEMO", "true");
      let avisar: (hayLentos: boolean) => void = () => {};
      vi.spyOn(clienteApi, "suscribirseALentitud").mockImplementation((oyente) => {
        avisar = oyente;
        return () => {};
      });

      render(<AvisoDemo />);
      expect(screen.getByRole("note")).not.toHaveTextContent("se está despertando");

      act(() => avisar(true));
      expect(screen.getByRole("note")).toHaveTextContent("estaba dormido y se está despertando");
      expect(screen.getByRole("note")).toHaveTextContent("No ingreses datos personales reales");

      act(() => avisar(false));
      expect(screen.getByRole("note")).not.toHaveTextContent("se está despertando");
    });

    it("deja de escuchar al desmontarse", () => {
      vi.stubEnv("VITE_MODO_DEMO", "true");
      const baja = vi.fn();
      vi.spyOn(clienteApi, "suscribirseALentitud").mockReturnValue(baja);

      render(<AvisoDemo />).unmount();

      expect(baja).toHaveBeenCalledTimes(1);
    });
  });
});
