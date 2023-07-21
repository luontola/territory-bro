#!/usr/bin/env bash
# workaround for https://stackoverflow.com/questions/69394632/webpack-build-failing-with-err-ossl-evp-unsupported
export NODE_OPTIONS=--openssl-legacy-provider
