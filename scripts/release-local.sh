#!/bin/bash
set -euo pipefail
#: ${1:? Usage: $0 RELEASE_VERSION}
DEFAULT_TAG=$(date +%Y-%m-%d)
TAG="${1:-$DEFAULT_TAG}"
echo TAG="$TAG"
set -x

export GIT_COMMIT=$(git rev-parse HEAD)
export BUILD_TIMESTAMP=$(date -Iseconds)
export RELEASE_VERSION="$TAG"

docker compose build --pull app

git tag "$TAG" "$GIT_COMMIT"

docker tag "luontola/territory-bro:latest" "luontola/territory-bro:$TAG"

docker push "luontola/territory-bro:$TAG"
docker push "luontola/territory-bro:latest"

git push origin "$TAG"
