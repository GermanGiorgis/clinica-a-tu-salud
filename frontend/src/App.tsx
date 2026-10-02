import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { AuthProvider } from "./context/AuthContext";
import { ProtectedRoute } from "./components/ProtectedRoute";
import { Header } from "./components/Header";
import { HomePage } from "./pages/HomePage";
import { LoginPage } from "./pages/LoginPage";
import { RegistroPage } from "./pages/RegistroPage";
import { MedicosPage } from "./pages/MedicosPage";
import { MedicoDetallePage } from "./pages/MedicoDetallePage";
import { MisTurnosPage } from "./pages/MisTurnosPage";
import { AgendaMedicoPage } from "./pages/AgendaMedicoPage";
import { ConsultaPage } from "./pages/ConsultaPage";
import { HistorialPage } from "./pages/HistorialPage";
import { MisAfiliacionesPage } from "./pages/MisAfiliacionesPage";
import { FacturaPage } from "./pages/FacturaPage";
import { HistorialPacientePage } from "./pages/HistorialPacientePage";
import { MetricasPage } from "./pages/MetricasPage";
import "./App.css";

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Header />
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/registro" element={<RegistroPage />} />
          <Route path="/medicos" element={<MedicosPage />} />
          <Route path="/medicos/:id" element={<MedicoDetallePage />} />
          <Route
            path="/mis-turnos"
            element={
              <ProtectedRoute rolesPermitidos={["PACIENTE"]}>
                <MisTurnosPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/agenda"
            element={
              <ProtectedRoute rolesPermitidos={["MEDICO", "ADMIN"]}>
                <AgendaMedicoPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/turnos/:turnoId/consulta"
            element={
              <ProtectedRoute>
                <ConsultaPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/historial"
            element={
              <ProtectedRoute rolesPermitidos={["PACIENTE"]}>
                <HistorialPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/mi-cobertura"
            element={
              <ProtectedRoute rolesPermitidos={["PACIENTE"]}>
                <MisAfiliacionesPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/turnos/:turnoId/factura"
            element={
              <ProtectedRoute rolesPermitidos={["MEDICO", "ADMIN"]}>
                <FacturaPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/pacientes/:pacienteId/historial"
            element={
              <ProtectedRoute rolesPermitidos={["MEDICO", "ADMIN"]}>
                <HistorialPacientePage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/metricas"
            element={
              <ProtectedRoute rolesPermitidos={["ADMIN"]}>
                <MetricasPage />
              </ProtectedRoute>
            }
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}
