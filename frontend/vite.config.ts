import react from '@vitejs/plugin-react';
import { loadEnv } from 'vite';
import { defineConfig } from 'vitest/config';

export default defineConfig(({ mode }) => {
  // The dev server forwards API calls to the backend. Point it somewhere other
  // than a local `./mvnw spring-boot:run` with FLIPFINDER_API_URL.
  const apiUrl = loadEnv(mode, '.', '').FLIPFINDER_API_URL || 'http://localhost:8081';
  const backend = { target: apiUrl, changeOrigin: true };

  return {
    plugins: [react()],
    server: {
      proxy: {
        '/api': backend,
        '/swagger-ui': backend,
        '/v3/api-docs': backend,
      },
    },
  };
});
