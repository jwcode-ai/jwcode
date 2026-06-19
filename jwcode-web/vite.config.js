import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { inspectorServer } from '@react-dev-inspector/vite-plugin'
import path from 'path'

const backendUrl = process.env.VITE_BACKEND_URL || 'http://localhost:8080'
const wsUrl = process.env.VITE_WS_URL || 'ws://localhost:8081'

export default defineConfig({
  plugins: [react(), inspectorServer()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: backendUrl,
        changeOrigin: true,
      },
      '/ws': {
        target: wsUrl,
        ws: true,
      },
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          'react-vendor': ['react', 'react-dom'],
          'state': ['zustand'],
          'markdown': ['react-markdown', 'prismjs'],
          'terminal': ['xterm', 'xterm-addon-fit', 'xterm-addon-attach'],
          'icons': ['lucide-react'],
          'panels': ['react-resizable-panels'],
        },
      },
    },
    chunkSizeWarningLimit: 600,
    sourcemap: false,
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: [],
  },
})
