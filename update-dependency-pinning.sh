#!/bin/bash
set -e

./gradlew -q --configure-on-demand --write-verification-metadata sha256 -x test \
  assemble \
  check \
  deviceCheck
