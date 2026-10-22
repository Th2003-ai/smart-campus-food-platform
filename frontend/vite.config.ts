import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// 开发环境：前端 5173，后端 8080（context-path 为 /api）
// 通过代理转发 /api，前端代码里只写相对路径，避免硬编码后端地址。
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    open: false,
    // 监听所有网卡，便于同一局域网内用 http://<本机IP>:5173 访问演示
    host: true,
    proxy: {
      '/api': {
        target: process.env.BACKEND_ORIGIN || 'http://localhost:8080',
        changeOrigin: true,
        // 允许 WebSocket 升级：订单实时通知走 /api/ws/order
        ws: true
      }
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    chunkSizeWarningLimit: 1500
  }
})
