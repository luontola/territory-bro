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
    react({
      // Fix to "org.graalvm.polyglot.PolyglotException: Error: Error reading: react/jsx-runtime"
      // https://github.com/vitejs/vite/issues/6215
      jsxRuntime: 'classic',
    }),
    //vike(),
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
  ssr: {
    external: ["http"],
    //noExternal: true
    //noExternal: ["i18next"]
    noExternal: /.*/
  },
  optimizeDeps: {
    //include: ['react/jsx-runtime']
  },
  resolve: {
    alias: {
      // Fix to "org.graalvm.polyglot.PolyglotException: Error: Error reading: react/jsx-runtime"
      //  'react/jsx-runtime': 'react/jsx-runtime.js'
    }
  },
  server: {
    port: 8080,
    proxy: {
      '/api': {
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
