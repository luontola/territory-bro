#!/bin/bash
set -eu
#: ${1:? Usage: $0 RELEASE_VERSION}
DEFAULT_TAG=`date +%Y-%m-%d`
TAG="${1:-$DEFAULT_TAG}"
echo TAG=$TAG
set -x

docker-compose build --pull

git tag "$TAG"

docker tag "luontola/territory-bro-api" "luontola/territory-bro-api:$TAG"
docker tag "luontola/territory-bro-web" "luontola/territory-bro-web:$TAG"

docker push "luontola/territory-bro-api:$TAG"
docker push "luontola/territory-bro-api:latest"
docker push "luontola/territory-bro-web:$TAG"
docker push "luontola/territory-bro-web:latest"

git push origin "$TAG"
