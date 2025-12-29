import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      // 代理后端API请求，解决CORS问题
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      }
    }
  }
})

