import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AxiosError, type AxiosResponse } from "axios";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as authApi from "../api/auth";
import { renderConSesion } from "../test/render";
import { RegistroPage } from "./RegistroPage";

vi.mock("../api/auth", async (importOriginal) => {
  const real = await importOriginal<typeof import("../api/auth")>();
  return { ...real, registrarPaciente: vi.fn() };
});

const respuestaOk: authApi.AuthResponse = { token: "jwt", email: "ana@clinica.test", nombre: "Ana", apellido: "Paz", rol: "PACIENTE" };

function montar() {
  renderConSesion(<RegistroPage />, { ruta: "/registro", path: "/registro" });
}

function falla(status: number, data: unknown) {
  return new AxiosError("x", "ERR_BAD_REQUEST", undefined, undefined, { status, data } as AxiosResponse);
}

async function llenar(password = "una-clave-segura-1") {
  await userEvent.type(screen.getByLabelText("Nombre"), "Ana");
  await userEvent.type(screen.getByLabelText("Apellido"), "Paz");
  await userEvent.type(screen.getByLabelText("Email"), "ana@clinica.test");
  await userEvent.type(screen.getByLabelText(/^Contraseña/), password);
  await userEvent.type(screen.getByLabelText(/^DNI/), "30111222");
  await userEvent.type(screen.getByLabelText("Fecha de nacimiento"), "1990-05-20");
}

async function completar(password?: string) {
  await llenar(password);
  await userEvent.click(screen.getByRole("button", { name: "Crear cuenta" }));
}

describe("RegistroPage", () => {
  beforeEach(() => {
    vi.mocked(authApi.registrarPaciente).mockReset();
  });

  it("pide una contraseña de entre 10 y 72 caracteres, igual que el servidor", () => {
    montar();

    const campo = screen.getByLabelText(/^Contraseña/);
    expect(campo).toHaveAttribute("minLength", "10");
    expect(campo).toHaveAttribute("maxLength", "72");
    expect(screen.getByText(/entre 10 y 72 caracteres/)).toBeInTheDocument();
  });

  it("el DNI acepta solo números: si lo pegan con puntos o letras queda limpio", async () => {
    montar();
    const dni = screen.getByLabelText(/^DNI/);

    await userEvent.type(dni, "30.111.222");

    expect(dni).toHaveValue("30111222");
    expect(dni).toHaveAttribute("inputMode", "numeric");
    await userEvent.clear(dni);
    await userEvent.type(dni, "ab12cd");
    expect(dni).toHaveValue("12");
  });

  it("avisa que la clínica tiene que verificar la identidad llevando el DNI", () => {
    montar();

    expect(screen.getByText(/verificar tu identidad/)).toBeInTheDocument();
  });

  it("manda los datos cargados, sin teléfono si se deja vacío, y va al inicio", async () => {
    vi.mocked(authApi.registrarPaciente).mockResolvedValue(respuestaOk);
    montar();

    await completar();

    await waitFor(() => expect(screen.getByTestId("ubicacion")).toHaveTextContent(/^\/$/));
    expect(authApi.registrarPaciente).toHaveBeenCalledWith({
      nombre: "Ana", apellido: "Paz", email: "ana@clinica.test", password: "una-clave-segura-1",
      telefono: undefined, dni: "30111222", fechaNacimiento: "1990-05-20",
    });
  });

  describe("cuando el servidor rechaza el alta, dice por qué y dónde", () => {
    it("muestra el mensaje del servidor si el alta falla (por ejemplo, por el límite de altas por IP)", async () => {
      vi.mocked(authApi.registrarPaciente).mockRejectedValue(falla(429, { error: "Demasiados intentos. Probá de nuevo en 40 minuto(s)" }));
      montar();

      await completar();

      expect(await screen.findByText("Demasiados intentos. Probá de nuevo en 40 minuto(s)")).toBeInTheDocument();
      expect(screen.getByTestId("ubicacion")).toHaveTextContent("/registro");
    });

    it("marca cada dato rechazado debajo de ESE campo, en castellano", async () => {
      vi.mocked(authApi.registrarPaciente).mockRejectedValue(falla(400, {
        error: "Revisá los datos ingresados",
        errores: { telefono: "tiene que tener entre 8 y 15 números", fechaNacimiento: "tiene que ser una fecha anterior a hoy" },
      }));
      montar();

      await completar();

      expect(await screen.findByText("Tiene que tener entre 8 y 15 números")).toBeInTheDocument();
      expect(screen.getByLabelText(/^Teléfono/)).toHaveAttribute("aria-invalid", "true");
      expect(screen.getByLabelText(/^Teléfono/)).toHaveAccessibleDescription("Tiene que tener entre 8 y 15 números");
      expect(screen.getByLabelText("Fecha de nacimiento")).toHaveAccessibleDescription("Tiene que ser una fecha anterior a hoy");
      expect(screen.getByLabelText("Nombre")).not.toHaveAttribute("aria-invalid");
      expect(screen.getByText(/Revisá los datos marcados en rojo/)).toBeInTheDocument();
    });

    it("si el servidor marca un campo que el formulario no conoce, lo cuenta arriba del botón", async () => {
      vi.mocked(authApi.registrarPaciente).mockRejectedValue(falla(400, {
        error: "Revisá los datos ingresados", errores: { campoNuevo: "no va" },
      }));
      montar();

      await completar();

      expect(await screen.findByText("Revisá los datos ingresados (campoNuevo: no va)")).toBeInTheDocument();
    });

    it("si el email ya existe lo dice debajo del email, ofrece iniciar sesión y no deja a medias la sesión", async () => {
      vi.mocked(authApi.registrarPaciente).mockRejectedValue(falla(409, { error: "Ya existe una cuenta con ese email" }));
      montar();

      await completar();

      expect(await screen.findByText("Ya existe una cuenta con ese email")).toBeInTheDocument();
      expect(screen.getByLabelText("Email")).toHaveAttribute("aria-invalid", "true");
      expect(screen.getByLabelText("Email")).toHaveFocus();
      expect(screen.getAllByRole("link", { name: "Iniciá sesión" })).toHaveLength(2);
      expect(localStorage.getItem("token")).toBeNull();
    });

    it("al cambiar el email repetido desaparece el enlace para iniciar sesión", async () => {
      vi.mocked(authApi.registrarPaciente).mockRejectedValue(falla(409, { error: "Ya existe una cuenta con ese email" }));
      montar();
      await completar();
      await screen.findByText("Ya existe una cuenta con ese email");

      await userEvent.type(screen.getByLabelText("Email"), "x");

      expect(screen.queryByText("Ya existe una cuenta con ese email")).not.toBeInTheDocument();
      expect(screen.getAllByRole("link", { name: "Iniciá sesión" })).toHaveLength(1);
    });

    it("si no hay conexión con el servidor lo dice y no pierde lo escrito", async () => {
      vi.mocked(authApi.registrarPaciente).mockRejectedValue(new AxiosError("Network Error", "ERR_NETWORK"));
      montar();

      await completar();

      expect(await screen.findByText(/No se pudo conectar con el servidor/)).toBeInTheDocument();
      expect(screen.getByLabelText("Nombre")).toHaveValue("Ana");
      expect(screen.getByLabelText("Email")).toHaveValue("ana@clinica.test");
      expect(screen.getByRole("button", { name: "Crear cuenta" })).toBeEnabled();
    });

    it("si el servidor falla por dentro lo dice con el código del error", async () => {
      vi.mocked(authApi.registrarPaciente).mockRejectedValue(falla(500, ""));
      montar();

      await completar();

      expect(await screen.findByText(/problema al procesar el pedido \(error 500\)/)).toBeInTheDocument();
    });
  });

  describe("revisa los datos antes de mandarlos y dice qué está mal en cada uno", () => {
    it("con el formulario vacío marca los obligatorios, no llama al servidor y enfoca el primero", async () => {
      montar();

      await userEvent.click(screen.getByRole("button", { name: "Crear cuenta" }));

      expect(screen.getByText("Escribí tu nombre.")).toBeInTheDocument();
      expect(screen.getByText("Escribí tu apellido.")).toBeInTheDocument();
      expect(screen.getByText("Escribí tu email.")).toBeInTheDocument();
      expect(screen.getByText("Elegí una contraseña.")).toBeInTheDocument();
      expect(screen.getByText(/Escribí tu DNI/)).toBeInTheDocument();
      expect(screen.getByText(/Elegí tu fecha de nacimiento/)).toBeInTheDocument();
      expect(screen.getByLabelText(/^Teléfono/)).not.toHaveAttribute("aria-invalid");
      expect(screen.getByText(/Revisá los datos marcados en rojo/)).toBeInTheDocument();
      expect(screen.getByLabelText("Nombre")).toHaveFocus();
      expect(authApi.registrarPaciente).not.toHaveBeenCalled();
    });

    it("un email sin dominio dice qué le falta", async () => {
      montar();
      await llenar();
      await userEvent.clear(screen.getByLabelText("Email"));
      await userEvent.type(screen.getByLabelText("Email"), "germanggiorgis@gmail");
      await userEvent.click(screen.getByRole("button", { name: "Crear cuenta" }));

      expect(await screen.findByText(/le falta la terminación del dominio/)).toBeInTheDocument();
      expect(authApi.registrarPaciente).not.toHaveBeenCalled();
    });

    it("una contraseña corta dice cuántos caracteres tiene y cuántos necesita", async () => {
      montar();
      await completar("corta");

      expect(await screen.findByText("Es muy corta: tiene 5 caracteres y necesita al menos 10.")).toBeInTheDocument();
      expect(authApi.registrarPaciente).not.toHaveBeenCalled();
    });

    it("un DNI corto y un teléfono con letras se marcan a la vez, cada uno con su causa", async () => {
      montar();
      await userEvent.type(screen.getByLabelText(/^Teléfono/), "261-ABC-1234");
      await userEvent.type(screen.getByLabelText(/^DNI/), "12345");
      await userEvent.click(screen.getByRole("button", { name: "Crear cuenta" }));

      expect(screen.getByText(/El DNI tiene entre 6 y 9 números y escribiste 5/)).toBeInTheDocument();
      expect(screen.getByText(/Solo puede tener números.*Sin letras/)).toBeInTheDocument();
    });

    it("una fecha de nacimiento futura se rechaza", async () => {
      montar();
      await llenar();
      const fecha = screen.getByLabelText("Fecha de nacimiento");
      await userEvent.clear(fecha);
      await userEvent.type(fecha, "2999-01-01");
      await userEvent.click(screen.getByRole("button", { name: "Crear cuenta" }));

      expect(await screen.findByText("La fecha de nacimiento tiene que ser anterior a hoy.")).toBeInTheDocument();
    });

    it("el aviso de un campo aparece al salir de él y desaparece apenas se corrige", async () => {
      montar();
      const email = screen.getByLabelText("Email");

      await userEvent.type(email, "ana@clinica");
      await userEvent.tab();
      expect(screen.getByText(/le falta la terminación del dominio/)).toBeInTheDocument();

      await userEvent.type(email, ".com");
      expect(screen.queryByText(/le falta la terminación/)).not.toBeInTheDocument();
      expect(email).not.toHaveAttribute("aria-invalid");
    });

    it("tras corregir, el siguiente intento manda los datos sin espacios sobrantes", async () => {
      vi.mocked(authApi.registrarPaciente).mockResolvedValue(respuestaOk);
      montar();
      await userEvent.click(screen.getByRole("button", { name: "Crear cuenta" }));
      expect(screen.getByText(/Revisá los datos marcados en rojo/)).toBeInTheDocument();

      await userEvent.type(screen.getByLabelText("Nombre"), "  Ana ");
      await userEvent.type(screen.getByLabelText("Apellido"), "Paz");
      await userEvent.type(screen.getByLabelText("Email"), " ana@clinica.test ");
      await userEvent.type(screen.getByLabelText(/^Contraseña/), "una-clave-segura-1");
      await userEvent.type(screen.getByLabelText(/^DNI/), "30111222");
      await userEvent.type(screen.getByLabelText("Fecha de nacimiento"), "1990-05-20");
      await userEvent.click(screen.getByRole("button", { name: "Crear cuenta" }));

      await waitFor(() => expect(authApi.registrarPaciente).toHaveBeenCalledWith(
        expect.objectContaining({ nombre: "Ana", email: "ana@clinica.test" }),
      ));
    });
  });
});
