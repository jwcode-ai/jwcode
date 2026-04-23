import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'ws://localhost:8081',
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
          'terminal': ['xterm', 'xterm-addon-fit'],
          'icons': ['lucide-react'],
          'panels': ['react-resizable-panels'],
        },
      },
    },
    chunkSizeWarningLimit: 600,
  },
})
