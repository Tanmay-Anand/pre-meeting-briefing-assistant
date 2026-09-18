import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
  ],

  build: {
    outDir: 'build',

    rollupOptions: {
      input: {
        main: './index.html',
        background: './src/background/background.ts',
        content: './src/content/content.ts',
      },

      output: {
        entryFileNames: (chunkInfo) => {
          if (chunkInfo.name === 'background') {
            return 'background.js'
          }

          if (chunkInfo.name === 'content') {
            return 'content.js'
          }

          return 'assets/[name]-[hash].js'
        },
      },
    },
  },
})