#!/bin/bash
set -e

profiles=$1

workdir=`pwd`

# Preinstall commons-vscode package
(cd ../commons-vscode ; npm install ; npm pack)
npm install ../commons-vscode/*-commons-vscode-*.tgz

# Clean old jars
rm -fr ${workdir}/jars
mkdir -p ${workdir}/jars

#Clean old LS folder
rm -fr ${workdir}/language-server
mkdir -p ${workdir}/language-server

# Use maven to build fat jar of the language server
cd ${workdir}/../../headless-services/spring-boot-language-server
./build.sh $profiles

# Extract LS JAR
cd ${workdir}/../../headless-services/spring-boot-language-server/target
server_jar_file=$(find . -name '*-exec.jar');
java -Djarmode=tools -jar $server_jar_file extract --destination ${workdir}/language-server

# JDT LS Extension
cd ${workdir}/../../headless-services/jdt-ls-extension
find . -name "*-sources.jar" -delete
cp org.springframework.tooling.jdt.ls.extension/target/*.jar ${workdir}/jars/jdt-ls-extension.jar
cp org.springframework.tooling.jdt.ls.commons/target/*.jar ${workdir}/jars/jdt-ls-commons.jar
cp org.springframework.tooling.gradle/target/*.jar ${workdir}/jars/sts-gradle-tooling.jar

# Copy Reactor dependency bundles
cp org.springframework.tooling.jdt.ls.commons/target/dependencies/io.projectreactor.reactor-core.jar ${workdir}/jars/
cp org.springframework.tooling.jdt.ls.commons/target/dependencies/org.reactivestreams.reactive-streams.jar ${workdir}/jars/

# XML LS Extension
cd ${workdir}/../../headless-services/xml-ls-extension
find . -name "*-sources.jar" -delete
cp target/*.jar ${workdir}/jars/xml-ls-extension.jar
cp target/dependencies/commons-lsp-extensions.jar ${workdir}/jars/

