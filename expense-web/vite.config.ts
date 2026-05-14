import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/system': {
        target: 'http://localhost:8081',
        bypass: (req) => req.headers.accept?.includes('text/html') ? '/index.html' : undefined
      },
      '/expense': {
        target: 'http://localhost:8082',
        bypass: (req) => req.headers.accept?.includes('text/html') ? '/index.html' : undefined
      },
      '/approval': {
        target: 'http://localhost:8083',
        bypass: (req) => req.headers.accept?.includes('text/html') ? '/index.html' : undefined
      },
      '/ai': {
        target: 'http://localhost:8084',
        bypass: (req) => req.headers.accept?.includes('text/html') ? '/index.html' : undefined
      },
      '/notification': {
        target: 'http://localhost:8085',
        bypass: (req) => req.headers.accept?.includes('text/html') ? '/index.html' : undefined
      }
    }
  }
})
