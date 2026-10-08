import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { renderConSesion } from "../test/render";
import { usuario } from "../test/fixtures";
import { Header } from "./Header";

describe("Header", () => {
  it("sin sesión muestra iniciar sesión y registrarme, y ningún enlace privado", () => {
    renderConSesion(<Header />);

    expect(screen.getByRole("link", { name: "Médicos" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Iniciar sesión" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Registrarme" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Mis turnos/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Mi agenda/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Salir/ })).not.toBeInTheDocument();
  });

  it("un paciente ve sus turnos, historial y cobertura, pero no la agenda ni las métricas", () => {
    renderConSesion(<Header />, { usuario: usuario("PACIENTE", { nombre: "Pablo" }) });

    expect(screen.getByRole("link", { name: /Mis turnos/ })).toHaveAttribute("href", "/mis-turnos");
    expect(screen.getByRole("link", { name: /Mi historial/ })).toHaveAttribute("href", "/historial");
    expect(screen.getByRole("link", { name: /Mi cobertura/ })).toHaveAttribute("href", "/mi-cobertura");
    expect(screen.queryByRole("link", { name: /Mi agenda/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Métricas/ })).not.toBeInTheDocument();
    expect(screen.getByText("Pablo")).toBeInTheDocument();
    expect(screen.getByText("PACIENTE")).toBeInTheDocument();
  });

  it("un médico ve su agenda, pero no las pantallas de paciente ni las métricas", () => {
    renderConSesion(<Header />, { usuario: usuario("MEDICO") });

    expect(screen.getByRole("link", { name: /Mi agenda/ })).toHaveAttribute("href", "/agenda");
    expect(screen.queryByRole("link", { name: /Mis turnos/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Mi historial/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Métricas/ })).not.toBeInTheDocument();
  });

  it("un admin ve la agenda y las métricas", () => {
    renderConSesion(<Header />, { usuario: usuario("ADMIN") });

    expect(screen.getByRole("link", { name: "Agenda" })).toHaveAttribute("href", "/agenda");
    expect(screen.getByRole("link", { name: /Métricas/ })).toHaveAttribute("href", "/metricas");
    expect(screen.queryByRole("link", { name: /Mis turnos/ })).not.toBeInTheDocument();
  });

  it("el admin ve verificar pacientes, feriados y la auditoría", () => {
    renderConSesion(<Header />, { usuario: usuario("ADMIN") });

    expect(screen.getByRole("link", { name: /Verificar pacientes/ })).toHaveAttribute("href", "/pacientes/pendientes");
    expect(screen.getByRole("link", { name: /Feriados y ausencias/ })).toHaveAttribute("href", "/dias-no-laborables");
    expect(screen.getByRole("link", { name: /Auditoría/ })).toHaveAttribute("href", "/auditoria");
  });

  it("la recepción ve lo del mostrador, pero no las métricas, la auditoría ni nada de paciente", () => {
    renderConSesion(<Header />, { usuario: usuario("RECEPCIONISTA") });

    expect(screen.getByRole("link", { name: "Agenda" })).toHaveAttribute("href", "/agenda");
    expect(screen.getByRole("link", { name: /Verificar pacientes/ })).toHaveAttribute("href", "/pacientes/pendientes");
    expect(screen.getByRole("link", { name: /Feriados y ausencias/ })).toHaveAttribute("href", "/dias-no-laborables");
    expect(screen.getByRole("link", { name: "Médicos" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Métricas/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Auditoría/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Mis turnos|Mi historial|Mi cobertura/ })).not.toBeInTheDocument();
    expect(screen.getByText("RECEPCIONISTA")).toBeInTheDocument();
  });

  it.each(["MEDICO", "PACIENTE"] as const)("un %s no ve verificar pacientes, feriados ni la auditoría", (rol) => {
    renderConSesion(<Header />, { usuario: usuario(rol) });

    expect(screen.queryByRole("link", { name: /Verificar pacientes/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Feriados y ausencias/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Auditoría/ })).not.toBeInTheDocument();
  });

  it("marca como activo el enlace de la sección actual", () => {
    renderConSesion(<Header />, { usuario: usuario("ADMIN"), ruta: "/metricas" });

    expect(screen.getByRole("link", { name: /Métricas/ })).toHaveClass("activo");
    expect(screen.getByRole("link", { name: "Agenda" })).not.toHaveClass("activo");
  });

  it("Salir cierra la sesión, borra el token y vuelve al inicio", async () => {
    renderConSesion(<Header />, { usuario: usuario("PACIENTE"), ruta: "/mis-turnos" });

    await userEvent.click(screen.getByRole("button", { name: /Salir/ }));

    expect(localStorage.getItem("token")).toBeNull();
    expect(screen.getByTestId("ubicacion")).toHaveTextContent("/");
    expect(screen.getByRole("link", { name: "Iniciar sesión" })).toBeInTheDocument();
  });
});
