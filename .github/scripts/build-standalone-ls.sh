#!/bin/bash
set -e -x

dist_type=$1

workdir=$(pwd)

echo "Building standalone LS jar..."
cd headless-services
./mvnw -f pom.xml -pl spring-boot-language-server-standalone -am -DskipTests clean package

# Get the version from Maven
base_version=$(./mvnw -q -f spring-boot-language-server-standalone/pom.xml -Dexec.executable=echo -Dexec.args='${project.version}' --non-recursive exec:exec | tail -n 1)

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
