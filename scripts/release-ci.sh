#!/bin/bash
set -euo pipefail
#: ${1:? Usage: $0 RELEASE_VERSION}
DEFAULT_TAG=$(date +%Y-%m-%d)
TAG="${1:-$DEFAULT_TAG}"
echo TAG="$TAG"
set -x

docker pull "luontola/territory-bro:ci"

GIT_COMMIT=$(docker inspect "luontola/territory-bro:ci" | jq -r '.[0].Config.Env[] | select(startswith("GIT_COMMIT=")) | split("=")[1]')

docker build \
  -t "luontola/territory-bro:latest" \
  --build-arg="RELEASE_VERSION=$TAG" \
  --platform=linux/amd64 \
  - <<EOF
FROM luontola/territory-bro:ci
ARG RELEASE_VERSION
ENV RELEASE_VERSION=\$RELEASE_VERSION
EOF

git tag "$TAG" "$GIT_COMMIT"

docker tag "luontola/territory-bro:latest" "luontola/territory-bro:$TAG"

docker push "luontola/territory-bro:$TAG"
docker push "luontola/territory-bro:latest"

git push origin "$TAG"
