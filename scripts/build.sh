#!/usr/bin/env bash
set -eux

docker compose up -d db

lein clean
lein test
lein uberjar

npm install
npm run test
npm run build
