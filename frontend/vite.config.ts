import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 3000,
    proxy: {
      // Regola specifica per WebSocket: deve stare PRIMA della regola /api
      // perché Vite usa il primo match. Il target usa ws:// per indicare
      // che il proxy deve gestire l'upgrade del protocollo WebSocket.
      '/api/ws': {
        target: 'ws://localhost:8080',
        ws: true,
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
      // Regola generica per tutte le altre chiamate HTTP verso il gateway
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
})
