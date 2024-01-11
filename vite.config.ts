// Copyright © 2015-2024 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {defineConfig} from 'vitest/config'
import react from '@vitejs/plugin-react'
import i18nextLoader from 'vite-plugin-i18next-loader'

// https://vitejs.dev/config/
// https://vitest.dev/config/
export default defineConfig({
  root: "web",
  plugins: [
    react(),
    i18nextLoader({
      paths: ['./web/src/locales'],
      namespaceResolution: 'basename',
    })
  ],
  build: {
    outDir: "../target/web-dist/public",
    emptyOutDir: true,
    sourcemap: true,
  },
  css: {
    modules: {
      generateScopedName: "[name]__[local]___[hash:base64:5]"
    }
  },
  server: {
    port: 8080,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/assets': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '^/congregation/[^\/]+/territories/[^\/]+': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      }
    }
  },
  test: {
    globals: true,
    environment: "jsdom",
    cache: {
      dir: "../node_modules/.vitest"
    }
  }
})
