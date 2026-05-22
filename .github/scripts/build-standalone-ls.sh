#!/bin/bash
set -e -x

dist_type=$1

workdir=$(pwd)

echo "Building standalone LS jar..."
cd headless-services
./mvnw -f pom.xml -pl spring-boot-language-server-standalone -am -DskipTests clean package

# Get the version from Maven using help:evaluate
base_version=$(./mvnw help:evaluate -Dexpression="project.version" -q -DforceStdout -f spring-boot-language-server-standalone/pom.xml)

# Strip -SNAPSHOT if it exists, to get the clean base version
base_version=${base_version%-SNAPSHOT}

if [ "$dist_type" = "snapshot" ]; then
    timestamp=$(date -u +%Y%m%d%H%M)
    base_version="${base_version}-${timestamp}"
elif [ "$dist_type" = "pre" ]; then
    timestamp=$(date -u +%Y%m%d%H)
    base_version="${base_version}-PRE-${timestamp}"
fi

cd "$workdir"

mkdir -p standalone-ls-dist
jar_file=$(ls headless-services/spring-boot-language-server-standalone/target/*-standalone-exec.jar | head -n 1)
cp $jar_file standalone-ls-dist/spring-boot-language-server-standalone-exec.jar

echo "$base_version" > standalone-ls-dist/version.txt
