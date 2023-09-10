#!/usr/bin/env bash
set -eux

export VITE_GIT_COMMIT=$(git rev-parse --short HEAD)

docker compose up -d db

lein clean
lein test
lein uberjar

npm install
npm run test
npm run build
