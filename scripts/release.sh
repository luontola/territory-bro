#!/bin/bash
set -eu
#: ${1:? Usage: $0 RELEASE_VERSION}

DEFAULT_TAG=`date +%Y-%m-%d`
TAG="${1:-$DEFAULT_TAG}"
echo TAG=$TAG

./scripts/build-uberjar.sh
. ./scripts/build-docker.sh

docker tag "$REPO" "$REPO:$TAG"
docker push "$REPO:$TAG"
docker push "$REPO:latest"
