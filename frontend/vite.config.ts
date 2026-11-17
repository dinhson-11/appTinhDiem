import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  build: {
    // Spring Boot phục vụ giao diện đã build từ thư mục static của backend.
    outDir: '../backend/src/main/resources/static',
    emptyOutDir: true,
  },
  server: {
    // Khi phát triển (npm run dev), chuyển /api sang Spring Boot ở cổng 8080.
    proxy: { '/api': 'http://localhost:8080' },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test-setup.ts',
  },
})
