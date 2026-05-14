import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/system': 'http://localhost:8081',
      '/expense': 'http://localhost:8082',
      '/approval': 'http://localhost:8083',
      '/ai': 'http://localhost:8084',
      '/notification': 'http://localhost:8085'
    }
  }
})
