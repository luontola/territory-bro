#!/bin/bash
set -eu
#: ${1:? Usage: $0 RELEASE_VERSION}
DEFAULT_TAG=$(date +%Y-%m-%d)
TAG="${1:-$DEFAULT_TAG}"
echo TAG="$TAG"
set -x

docker compose build --pull app

git tag "$TAG"

docker tag "luontola/territory-bro:latest" "luontola/territory-bro:$TAG"

docker push "luontola/territory-bro:$TAG"
docker push "luontola/territory-bro:latest"

git push origin "$TAG"
