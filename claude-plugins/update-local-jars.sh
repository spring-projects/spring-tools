#!/bin/bash
set -e

# Go to the directory of this script (claude-plugins)
cd "$(dirname "$0")"

echo "Building standalone language server..."
cd ../headless-services
./mvnw -f pom.xml -pl spring-boot-language-server-standalone -am -DskipTests clean package
cd ../claude-plugins

echo "Updating local JARs for Claude Code plugins..."

# 1. Update spring-boot plugin
echo "Updating spring-boot plugin..."
SPRING_BOOT_JAR_DIR="spring-boot/language-server"
mkdir -p "$SPRING_BOOT_JAR_DIR"

# Find and copy the jar
jar_file=$(ls ../headless-services/spring-boot-language-server-standalone/target/*-standalone-exec.jar | head -n 1)

if [ -f "$jar_file" ]; then
    cp "$jar_file" "$SPRING_BOOT_JAR_DIR/spring-boot-language-server-standalone-exec.jar"
    echo "  Successfully copied to $SPRING_BOOT_JAR_DIR/spring-boot-language-server-standalone-exec.jar"
else
    echo "  Error: Could not find the standalone JAR."
    exit 1
fi

echo "Done!"