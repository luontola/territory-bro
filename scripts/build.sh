#!/usr/bin/env bash
set -eux

docker-compose up -d db
./scripts/build-uberjar.sh
yarn install
yarn run flow check
yarn run test
yarn run build
docker-compose build --pull
