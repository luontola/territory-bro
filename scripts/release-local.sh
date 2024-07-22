#!/bin/bash
set -euo pipefail
#: ${1:? Usage: $0 RELEASE_VERSION}
DEFAULT_TAG=$(date +%Y-%m-%d)
TAG="${1:-$DEFAULT_TAG}"
echo TAG="$TAG"
set -x

RELEASE_VERSION="$TAG" ./scripts/build.sh

git tag "$TAG"

docker tag "luontola/territory-bro:latest" "luontola/territory-bro:$TAG"

docker push "luontola/territory-bro:$TAG"
docker push "luontola/territory-bro:latest"

git push origin "$TAG"
