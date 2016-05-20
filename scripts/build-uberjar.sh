#!/bin/bash
set -eux

lein clean
lein test
lein uberjar
