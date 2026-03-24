#!/usr/bin/env bash

set -euo pipefail

shopt -s nullglob

for jar in *.jar; do
  echo "Processing: $jar"

  # Remove signature-related files if they exist
  zip -q -d "$jar" \
    'META-INF/*.SF' \
    'META-INF/*.RSA' \
    'META-INF/*.DSA' \
    'META-INF/*.EC' || true
done

echo "Done."
