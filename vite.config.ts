// Copyright © 2015-2023 Esko Luontola
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
    outDir: "../target/web-dist",
    emptyOutDir: true,
    sourcemap: true,
  },
  server: {
    port: 8080,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      }
    }
  },
  test: {
    cache: {
      dir: "../node_modules/.vitest"
    }
  }
})
