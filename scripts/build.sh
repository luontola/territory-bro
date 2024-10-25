#!/usr/bin/env bash
set -euxo pipefail

docker compose up -d db

lein clean

npm install
npm run test
npm run build

# macOS find doesn't support -regextype, and GNU find doesn't support -E
if [[ "$OSTYPE" == "darwin"* ]]; then
  find -E target/web-dist/public resources/public/assets \
    -type f \
    -iregex '.*\.(html|js|map|css|svg|otf|ttf|txt|json)' \
    -print -exec zopfli '{}' \; -exec brotli -f '{}' \;
else
  find target/web-dist/public resources/public/assets \
    -type f \
    -regextype posix-extended \
    -iregex '.*\.(html|js|map|css|svg|otf|ttf|txt|json)' \
    -print -exec zopfli '{}' \; -exec brotli -f '{}' \;
fi
# don't create compressed files under the source tree, but move them away
# to avoid breaking some tests and leaving around files that shouldn't be committed
find resources/public/assets \
  -type f \( -name "*.gz" -o -name "*.br" \) \
  -exec bash -c 'mv "$1" "target/web-dist/public/${1#resources/public/assets/}"' _ {} \;

lein do kaocha fast slow, uberjar

# AppCDS preparation: dump list of loaded classes during e2e test run
java -XX:DumpLoadedClassList=target/uberjar/classes.list -Dconf=dev-config.edn -jar "target/uberjar/territory-bro.jar" &> target/uberjar/warmup.log &
warmup=$!
# Gives the best coverage in the least time:
# - registration-test visits nearly every page
# - gis-access-test syncs GIS data and projects the events
# - login-and-logout-test goes through the Auth0 OIDC login flow
lein kaocha e2e --focus territory-bro.browser-test/login-and-logout-test
kill $warmup
wait $warmup || true

export GIT_COMMIT=$(git rev-parse HEAD)
export BUILD_TIMESTAMP=$(date -Iseconds)
export BUILDKIT_PROGRESS=plain
docker compose build --pull app

export DEMO_CONGREGATION=$(cat target/test-congregation-id)
docker compose up -d app
lein kaocha e2e --skip-meta prod
