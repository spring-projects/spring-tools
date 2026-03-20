#!/bin/bash
set -e
ANTLR_VERSION=4.13.2
if [ ! -f antlr-${ANTLR_VERSION}.jar ]; then
    curl https://www.antlr.org/download/antlr-${ANTLR_VERSION}-complete.jar --output antlr-${ANTLR_VERSION}.jar
fi
workdir=$(pwd)
cd resources
java -jar ${workdir}/antlr-${ANTLR_VERSION}.jar JavaProperties.g4 -o ${workdir}/src/main/java/org/springframework/ide/vscode/java/properties/antlr/parser -package org.springframework.ide.vscode.java.properties.antlr.parser

