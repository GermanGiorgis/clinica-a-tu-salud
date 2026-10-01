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
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}
