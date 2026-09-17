import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [
    react()
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