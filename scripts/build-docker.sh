#!/bin/bash
set -eu
REPO="luontola/territory-bro"
set -x

docker build --pull --tag "$REPO" .
