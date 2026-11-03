/// <reference types="vitest" />
import { fileURLToPath, URL } from 'node:url';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// In development the Vite server proxies /api to the backend, so the SPA and API share an origin
// (the refresh-token cookie is SameSite=Strict). In production nginx does the same.
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080', changeOrigin: true },
    },
  },
  build: {
    sourcemap: true,
    rollupOptions: {
      output: {
        // Long-lived vendor chunks cache well across deployments of app code.
        manualChunks: {
          react: ['react', 'react-dom', 'react-router-dom'],
          data: ['@tanstack/react-query', 'react-hook-form', 'zod', '@hookform/resolvers'],
          charts: ['recharts'],
        },
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    css: false,
    // Let Vite transform these ESM packages; Node 22's require(esm) otherwise hits an import cycle.
    server: { deps: { inline: [/@tanstack/] } },
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/**/*.test.{ts,tsx}', 'src/test/**', 'src/main.tsx'],
    },
  },
});
