import { resolve } from 'node:path'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { crx } from '@crxjs/vite-plugin'
import manifest from './manifest.config.ts'

// Builds to frontend/dist/, which is what .github/workflows/frontend-ci.yml uploads.
// Do not change outDir without updating that workflow.
export default defineConfig({
  plugins: [react(), crx({ manifest })],
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    rollupOptions: {
      // The panel is reached via web_accessible_resources, not via a manifest field that
      // crxjs treats as an entry point. Without this it would be copied verbatim and its
      // <script src="./main.tsx"> would 404 in the packed extension.
      input: {
        panel: resolve(import.meta.dirname, 'src/panel/index.html'),
      },
    },
  },
  server: {
    port: 5173,
    strictPort: true,
  },
})
