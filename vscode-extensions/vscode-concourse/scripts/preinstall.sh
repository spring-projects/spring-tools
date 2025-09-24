#!/bin/bash
set -e

profiles=$1

workdir=`pwd`

# Preinstall commons-vscode package
(cd ../commons-vscode ; npm install ; npm pack)
npm install ../commons-vscode/*-commons-vscode-*.tgz

# Use maven to build fat jar of the language server
cd ../../headless-services/concourse-language-server
./build.sh $profiles

#Clean old LS folder
rm -fr ${workdir}/language-server
mkdir -p ${workdir}/language-server

# Extract LS JAR
cd ${workdir}/../../headless-services/concourse-language-server/target
server_jar_file=$(find . -name '*-exec.jar');
java -Djarmode=tools -jar $server_jar_file extract --destination ${workdir}/language-server
