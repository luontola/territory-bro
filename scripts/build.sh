#!/usr/bin/env bash
set -eux

export VITE_GIT_COMMIT=$(git rev-parse --short HEAD)

docker compose up -d db

lein clean

npm install
npm run test
npm run build

# macOS find doesn't support -regextype, and GNU find doesn't support -E
if [[ "$OSTYPE" == "darwin"* ]]; then
  find -E target/web-dist/public \
    -type f \
    -iregex '.*\.(html|js|map|css|svg|otf|ttf|txt|json)' \
    -print -exec zopfli '{}' \; -exec brotli -f '{}' \;
else
  find target/web-dist/public \
    -type f \
    -regextype posix-extended \
    -iregex '.*\.(html|js|map|css|svg|otf|ttf|txt|json)' \
    -print -exec zopfli '{}' \; -exec brotli -f '{}' \;
fi

lein do kaocha fast slow, uberjar

java -XX:DumpLoadedClassList=target/uberjar/classes.list -Dconf=test-config.edn -jar "target/uberjar/territory-bro.jar" app-cds-setup

docker compose build --pull app
