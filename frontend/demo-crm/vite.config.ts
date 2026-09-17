import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Port 5174 is referenced by the extension manifest's content_scripts matches and by
// DemoCrmAdapter's base URL. Changing it means changing both.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5174,
    strictPort: true,
    proxy: {
      // The Demo CRM's data comes from the Spring Boot app's democrm controller, so the
      // adapter is calling a genuine HTTP API rather than reading its own database (G.4).
      '/api': 'http://localhost:8080',
    },
  },
})
