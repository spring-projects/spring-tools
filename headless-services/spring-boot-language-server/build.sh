#!/bin/bash
set -e

profiles=$1

modules=spring-boot-language-server,sts-gradle-model-plugin,:org.springframework.tooling.gradle,:org.springframework.tooling.jdt.ls.extension,:org.springframework.tooling.jdt.ls.commons,:org.springframework.tooling.jdt.ls.commons.test
cd ../jdt-ls-extension
if command -v xvfb-run ; then
    echo "Using xvfb to run in headless environment..."
    xvfb-run ../mvnw \
        -DtrimStackTrace=false \
        -f ../pom.xml \
        -pl $modules \
        -am \
        -B \
        clean install $profiles
else
    ../mvnw \
        -DtrimStackTrace=false \
        -f ../pom.xml \
        -pl $modules \
        -am \
        -B \
        clean install $profiles
fi
cd ../xml-ls-extension
    ../mvnw \
        -DtrimStackTrace=false \
        -f ../pom.xml \
        -pl xml-ls-extension \
        -am \
        -B \
        clean install $profiles
