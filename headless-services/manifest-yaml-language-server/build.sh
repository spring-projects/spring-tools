#!/bin/bash
set -e

# Use maven to build fat jar of the language server
../mvnw -B -f ../pom.xml -pl manifest-yaml-language-server -am clean install

