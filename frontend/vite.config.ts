import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // El backend permite CORS desde este origen por defecto (cors.allowed-origin).
  server: { port: 5176, strictPort: true },
})
