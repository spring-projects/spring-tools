#!/bin/bash
# Builds the standalone Spring Boot Language Server jar and installs it into
# this plugin's language-server/ directory.
#
# Usage: ./build.sh
#
# Requirements:
#   - Java 21+
#   - Maven wrapper (mvnw) available at the repo root
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
HEADLESS_SERVICES="${REPO_ROOT}/headless-services"
OUTPUT_DIR="${SCRIPT_DIR}/language-server"

echo "Building spring-boot-language-server-standalone..."
cd "${HEADLESS_SERVICES}"
./mvnw \
  -f pom.xml \
  -pl spring-boot-language-server-standalone \
  -am \
  -DskipTests \
  clean package

echo "Copying jar to plugin language-server/ directory..."
mkdir -p "${OUTPUT_DIR}"
cp "${HEADLESS_SERVICES}/spring-boot-language-server-standalone/target/"*-standalone-exec.jar \
   "${OUTPUT_DIR}/spring-boot-language-server-standalone-exec.jar"

echo "Done. Jar installed at: ${OUTPUT_DIR}/spring-boot-language-server-standalone-exec.jar"
