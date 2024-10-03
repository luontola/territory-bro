#!/usr/bin/env bash
set -euxo pipefail

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

java -XX:DumpLoadedClassList=target/uberjar/classes.list -Dconf=dev-config.edn -jar "target/uberjar/territory-bro.jar" app-cds-setup

export GIT_COMMIT=$(git rev-parse HEAD)
export BUILD_TIMESTAMP=$(date -Iseconds)
export BUILDKIT_PROGRESS=plain
docker compose build --pull app

docker compose up -d app
timeout 1m bash -c 'until curl --silent --fail http://localhost:8080/status; do sleep 5; done; echo'
lein kaocha e2e --skip territory-bro.browser-test/demo-test
