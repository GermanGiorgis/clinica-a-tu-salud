import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AxiosError, type AxiosResponse } from "axios";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import * as especialidadesApi from "../api/especialidades";
import * as usuariosApi from "../api/usuarios";
import { usuario } from "../test/fixtures";
import { renderConSesion } from "../test/render";
import type { UsuarioListado } from "../types";
import { UsuariosPage } from "./UsuariosPage";

vi.mock("../api/usuarios");
vi.mock("../api/especialidades");

function cuenta(extra: Partial<UsuarioListado> = {}): UsuarioListado {
  return { id: 1, email: "ana@clinica.test", nombre: "Ana", apellido: "Paz", telefono: null, rol: "PACIENTE", activo: true, perfilId: 4, ...extra };
}

const admin = cuenta({ id: 99, email: "admin@clinica.test", nombre: "Admin", apellido: "Sistema", rol: "ADMIN", perfilId: null });
const medica = cuenta({ id: 2, email: "marta@clinica.test", nombre: "Marta", apellido: "Gómez", rol: "MEDICO" });
const baja = cuenta({ id: 3, email: "lucas@clinica.test", nombre: "Lucas", apellido: "Rey", rol: "RECEPCIONISTA", activo: false, perfilId: null });

function errorApi(status: number, mensaje: string, errores?: Record<string, string>) {
  return new AxiosError("x", "ERR_BAD_REQUEST", undefined, undefined, { status, data: { error: mensaje, errores } } as AxiosResponse);
}

function montar() {
  renderConSesion(<UsuariosPage />, { usuario: usuario("ADMIN", { email: "admin@clinica.test" }), ruta: "/usuarios", path: "/usuarios" });
}

describe("UsuariosPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.spyOn(window, "confirm").mockReturnValue(true);
    vi.mocked(usuariosApi.listarUsuarios).mockResolvedValue([admin, cuenta(), medica, baja]);
    vi.mocked(especialidadesApi.listarEspecialidades).mockResolvedValue([
      { id: 1, nombre: "Dermatología", descripcion: null },
      { id: 2, nombre: "Traumatología y Ortopedia", descripcion: null },
    ]);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("lista las cuentas con su tipo y si están activas o de baja", async () => {
    montar();

    expect(await screen.findByText("Marta Gómez")).toBeInTheDocument();
    const fila = screen.getByText("Lucas Rey").closest("li")!;
    expect(within(fila).getByText("Recepción")).toBeInTheDocument();
    expect(within(fila).getByText("De baja")).toBeInTheDocument();
    expect(within(screen.getByText("Marta Gómez").closest("li")!).getByText("Activo")).toBeInTheDocument();
  });

  it("no ofrece dar de baja a la propia cuenta", async () => {
    montar();

    await screen.findByText("Marta Gómez");

    expect(screen.queryByRole("button", { name: /a Admin Sistema/ })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Dar de baja a Marta Gómez" })).toBeInTheDocument();
  });

  it("filtra por tipo de cuenta y busca por nombre o email sin importar tildes", async () => {
    montar();
    await screen.findByText("Marta Gómez");

    await userEvent.selectOptions(screen.getByLabelText("Mostrar"), "Médico");
    expect(screen.getByText("Marta Gómez")).toBeInTheDocument();
    expect(screen.queryByText("Ana Paz")).not.toBeInTheDocument();

    await userEvent.selectOptions(screen.getByLabelText("Mostrar"), "Todos los tipos");
    await userEvent.type(screen.getByLabelText(/Buscar/), "gomez");
    expect(screen.getByText("Marta Gómez")).toBeInTheDocument();
    expect(screen.queryByText("Ana Paz")).not.toBeInTheDocument();
  });

  it("si ningún usuario coincide con el filtro lo dice", async () => {
    montar();
    await screen.findByText("Marta Gómez");

    await userEvent.type(screen.getByLabelText(/Buscar/), "zzz");

    expect(screen.getByText("No hay usuarios que coincidan.")).toBeInTheDocument();
  });

  it("dar de baja pide confirmación, avisa lo que implica y recarga la lista", async () => {
    vi.mocked(usuariosApi.cambiarActivo).mockResolvedValue();
    montar();

    await userEvent.click(await screen.findByRole("button", { name: "Dar de baja a Marta Gómez" }));

    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining("los pacientes dejan de verlo"));
    await waitFor(() => expect(usuariosApi.cambiarActivo).toHaveBeenCalledWith(2, false));
    expect(await screen.findByText("Marta Gómez quedó dado de baja.")).toBeInTheDocument();
    expect(usuariosApi.listarUsuarios).toHaveBeenCalledTimes(2);
  });

  it("si no se confirma la baja no pasa nada", async () => {
    vi.mocked(window.confirm).mockReturnValue(false);
    montar();

    await userEvent.click(await screen.findByRole("button", { name: "Dar de baja a Marta Gómez" }));

    expect(usuariosApi.cambiarActivo).not.toHaveBeenCalled();
  });

  it("reactivar una cuenta de baja no pide confirmación", async () => {
    vi.mocked(usuariosApi.cambiarActivo).mockResolvedValue();
    montar();

    await userEvent.click(await screen.findByRole("button", { name: "Reactivar a Lucas Rey" }));

    await waitFor(() => expect(usuariosApi.cambiarActivo).toHaveBeenCalledWith(3, true));
    expect(window.confirm).not.toHaveBeenCalled();
    expect(await screen.findByText("Lucas Rey volvió a estar activo.")).toBeInTheDocument();
  });

  it("si la baja falla muestra el motivo del servidor", async () => {
    vi.mocked(usuariosApi.cambiarActivo).mockRejectedValue(errorApi(400, "No podés desactivar tu propia cuenta"));
    montar();

    await userEvent.click(await screen.findByRole("button", { name: "Dar de baja a Marta Gómez" }));

    expect(await screen.findByText("No podés desactivar tu propia cuenta")).toBeInTheDocument();
  });

  describe("nuevo usuario", () => {
    async function abrirFormulario() {
      montar();
      await screen.findByText("Marta Gómez");
      await userEvent.click(screen.getByRole("button", { name: /Nuevo usuario/ }));
      return screen.getByRole("form", { name: "Nuevo usuario" });
    }

    async function completarBase(form: HTMLElement) {
      await userEvent.type(within(form).getByLabelText("Nombre"), "  Rita ");
      await userEvent.type(within(form).getByLabelText("Apellido"), "Mostrador");
      await userEvent.type(within(form).getByLabelText("Email"), "rita@clinica.test");
      await userEvent.type(within(form).getByLabelText(/^Contraseña/), "una-clave-segura-1");
    }

    it("crea una recepcionista con los datos básicos (sin campos de médico ni de paciente)", async () => {
      vi.mocked(usuariosApi.crearUsuario).mockResolvedValue(cuenta({ id: 7, nombre: "Rita", apellido: "Mostrador", rol: "RECEPCIONISTA" }));
      const form = await abrirFormulario();
      expect(within(form).queryByLabelText("Matrícula")).not.toBeInTheDocument();
      expect(within(form).queryByLabelText(/^DNI/)).not.toBeInTheDocument();

      await completarBase(form);
      await userEvent.click(within(form).getByRole("button", { name: /Crear cuenta/ }));

      await waitFor(() =>
        expect(usuariosApi.crearUsuario).toHaveBeenCalledWith({
          rol: "RECEPCIONISTA", nombre: "Rita", apellido: "Mostrador", email: "rita@clinica.test",
          password: "una-clave-segura-1", telefono: undefined,
        }),
      );
      expect(await screen.findByText("Se creó la cuenta de Rita Mostrador (Recepción).")).toBeInTheDocument();
      expect(screen.queryByRole("form", { name: "Nuevo usuario" })).not.toBeInTheDocument();
      expect(usuariosApi.listarUsuarios).toHaveBeenCalledTimes(2);
    });

    it("un médico pide matrícula y especialidades, y las manda", async () => {
      vi.mocked(usuariosApi.crearUsuario).mockResolvedValue(cuenta({ id: 8, rol: "MEDICO" }));
      const form = await abrirFormulario();

      await userEvent.selectOptions(within(form).getByLabelText("Tipo de cuenta"), "Médico");
      await completarBase(form);
      await userEvent.type(within(form).getByLabelText("Matrícula"), "MP-5050");
      await userEvent.click(within(form).getByLabelText("Traumatología y Ortopedia"));
      await userEvent.click(within(form).getByLabelText("Dermatología"));
      await userEvent.click(within(form).getByRole("button", { name: /Crear cuenta/ }));

      await waitFor(() =>
        expect(usuariosApi.crearUsuario).toHaveBeenCalledWith(
          expect.objectContaining({ rol: "MEDICO", matricula: "MP-5050", especialidadIds: [2, 1] }),
        ),
      );
    });

    it("una especialidad tildada se puede destildar", async () => {
      vi.mocked(usuariosApi.crearUsuario).mockResolvedValue(cuenta({ rol: "MEDICO" }));
      const form = await abrirFormulario();
      await userEvent.selectOptions(within(form).getByLabelText("Tipo de cuenta"), "Médico");
      await completarBase(form);
      await userEvent.type(within(form).getByLabelText("Matrícula"), "MP-1");

      await userEvent.click(within(form).getByLabelText("Dermatología"));
      await userEvent.click(within(form).getByLabelText("Dermatología"));
      await userEvent.click(within(form).getByRole("button", { name: /Crear cuenta/ }));

      await waitFor(() => expect(usuariosApi.crearUsuario).toHaveBeenCalledWith(expect.objectContaining({ especialidadIds: [] })));
    });

    it("un paciente pide DNI y fecha de nacimiento, y el DNI queda solo con números", async () => {
      vi.mocked(usuariosApi.crearUsuario).mockResolvedValue(cuenta({ id: 9 }));
      const form = await abrirFormulario();

      await userEvent.selectOptions(within(form).getByLabelText("Tipo de cuenta"), "Paciente");
      await completarBase(form);
      await userEvent.type(within(form).getByLabelText(/^DNI/), "30.111.222");
      await userEvent.type(within(form).getByLabelText("Fecha de nacimiento"), "1990-05-20");
      await userEvent.click(within(form).getByRole("button", { name: /Crear cuenta/ }));

      await waitFor(() =>
        expect(usuariosApi.crearUsuario).toHaveBeenCalledWith(expect.objectContaining({ rol: "PACIENTE", dni: "30111222", fechaNacimiento: "1990-05-20" })),
      );
      expect(vi.mocked(usuariosApi.crearUsuario).mock.calls[0][0]).not.toHaveProperty("matricula");
    });

    it("si el servidor rechaza los datos, el error sale en el formulario con nombres que se entienden y no se pierde lo escrito", async () => {
      vi.mocked(usuariosApi.crearUsuario).mockRejectedValue(errorApi(400, "Revisá los datos ingresados", { fechaNacimiento: "debe ser una fecha pasada" }));
      const form = await abrirFormulario();
      await userEvent.selectOptions(within(form).getByLabelText("Tipo de cuenta"), "Paciente");
      await completarBase(form);
      await userEvent.type(within(form).getByLabelText(/^DNI/), "30111222");
      await userEvent.type(within(form).getByLabelText("Fecha de nacimiento"), "2999-01-01");

      await userEvent.click(within(form).getByRole("button", { name: /Crear cuenta/ }));

      expect(await within(form).findByText(/Fecha de nacimiento: debe ser una fecha pasada/)).toBeInTheDocument();
      expect(within(form).getByLabelText("Email")).toHaveValue("rita@clinica.test");
    });

    it("un email repetido muestra el 409 del servidor", async () => {
      vi.mocked(usuariosApi.crearUsuario).mockRejectedValue(errorApi(409, "Ya existe una cuenta con ese email"));
      const form = await abrirFormulario();
      await completarBase(form);

      await userEvent.click(within(form).getByRole("button", { name: /Crear cuenta/ }));

      expect(await within(form).findByText("Ya existe una cuenta con ese email")).toBeInTheDocument();
    });

    it("cancelar cierra el formulario sin crear nada", async () => {
      const form = await abrirFormulario();

      await userEvent.click(within(form).getByRole("button", { name: "Cancelar" }));

      expect(screen.queryByRole("form", { name: "Nuevo usuario" })).not.toBeInTheDocument();
      expect(usuariosApi.crearUsuario).not.toHaveBeenCalled();
    });
  });

  it("si falla la carga de la lista muestra el error", async () => {
    vi.mocked(usuariosApi.listarUsuarios).mockRejectedValue(errorApi(403, "No tenés permiso para hacer esto"));
    montar();

    expect(await screen.findByText("No tenés permiso para hacer esto")).toBeInTheDocument();
  });
});
