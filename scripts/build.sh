#!/usr/bin/env bash
set -eux

docker-compose up -d db

lein clean
lein test
lein uberjar

yarn install
yarn run flow check
yarn run test
yarn run build
