#!/bin/bash
set -eu
#: ${1:? Usage: $0 RELEASE_VERSION}
DEFAULT_TAG=$(date +%Y-%m-%d)
TAG="${1:-$DEFAULT_TAG}"
echo TAG="$TAG"
set -x

docker pull "luontola/territory-bro:ci"

git tag "$TAG"

docker tag "luontola/territory-bro:ci" "luontola/territory-bro:$TAG"
docker tag "luontola/territory-bro:ci" "luontola/territory-bro:latest"

docker push "luontola/territory-bro:$TAG"
docker push "luontola/territory-bro:latest"

git push origin "$TAG"
