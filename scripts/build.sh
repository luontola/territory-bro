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
  -exec bash -c 'mv "$1" "target/web-dist/public/assets/${1#resources/public/assets/}"' _ {} \;

lein do kaocha fast slow, uberjar

export GIT_COMMIT=$(git rev-parse HEAD)
export BUILD_TIMESTAMP=$(date -Iseconds)
export BUILDKIT_PROGRESS=plain

# application warmup training run
docker compose -f docker-compose.yml build --pull app
docker compose up -d app
# gives the best coverage in the least time:
# - registration-test visits nearly every page
# - gis-access-test syncs GIS data and projects the events
# - login-and-logout-test goes through the Auth0 OIDC login flow
lein kaocha e2e --focus territory-bro.browser-test/login-and-logout-test
docker compose logs app > target/warmup.log
docker compose stop app

# build an optimized image based on the training run
docker compose -f docker-compose.yml -f docker-compose.build.yml build app

# run e2e tests against the final docker image
export DEMO_CONGREGATION=$(cat target/test-congregation-id)
docker compose up -d app
lein kaocha e2e --skip-meta prod
