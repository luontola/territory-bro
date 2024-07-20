// Copyright © 2015-2024 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {defineConfig} from 'vitest/config'
import i18nextLoader from 'vite-plugin-i18next-loader'
import fs from "fs";
import * as path from "path";
import * as child_process from "child_process";
import md5 from "crypto-js/md5";

function serverExport() {
  return {
    name: 'server-export',
    writeBundle(options, bundle) {
      console.log(child_process.execSync("npm run server-export").toString());
    },
  };
}

const cssModulesJson = {}

// https://vitejs.dev/config/
// https://vitest.dev/config/
export default defineConfig(({command}) => ({
  root: "web",
  plugins: [
    i18nextLoader({
      paths: ['./web/src/locales'],
      namespaceResolution: 'basename',
    }),
    serverExport(),
  ],
  build: {
    outDir: "../target/web-dist/public",
    emptyOutDir: true,
    sourcemap: true,
  },
  css: {
    modules: {
      getJSON: (cssFileName: string,
                json: Record<string, string>,
                _outputFileName: string) => {
        // The generated CSS class names differ between production and development mode.
        // We should write css-modules.json only when building the static CSS assets.
        // If "vite dev" would update css-modules.json, some of the CSS class names would
        // get out of sync with the previously built CSS file.
        if (command === 'build') {
          const match = cssFileName.match(/\/(\w+)\.module\.css/)
          const key = match ? match[1] : cssFileName
          cssModulesJson[key] = json;
          const file = "target/web-dist/css-modules.json";
          fs.mkdirSync(path.dirname(file), {recursive: true})
          fs.writeFileSync(file, JSON.stringify(cssModulesJson, null, 2))
        }
      },
      generateScopedName: function (local, filename, css) {
        filename = filename.split('?')[0]; // remove "?used" suffix
        filename = path.basename(filename, ".module.css"); // remove .module.css suffix
        const hash = md5(css).toString().substring(0, 8);
        return `${filename}__${local}--${hash}`;
      },
    }
  },
  test: {
    globals: true,
    environment: "jsdom",
    cache: {
      dir: "../node_modules/.vitest"
    }
  }
}))
