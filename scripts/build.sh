#!/usr/bin/env bash
set -eux

docker-compose up -d db
./scripts/build-uberjar.sh
pnpm install
pnpm run build
docker-compose build --pull
