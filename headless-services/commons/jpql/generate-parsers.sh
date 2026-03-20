#!/bin/bash
set -e
ANTLR_VERSION=4.13.2
if [ ! -f antlr-${ANTLR_VERSION}.jar ]; then
    curl https://www.antlr.org/download/antlr-${ANTLR_VERSION}-complete.jar --output antlr-${ANTLR_VERSION}.jar
fi
workdir=$(pwd)
cd grammars
java -jar ${workdir}/antlr-${ANTLR_VERSION}.jar Jpql.g4 -o ${workdir}/src/main/java/org/springframework/ide/vscode/parser/jpql -package org.springframework.ide.vscode.parser.jpql
java -jar ${workdir}/antlr-${ANTLR_VERSION}.jar Hql.g4 -o ${workdir}/src/main/java/org/springframework/ide/vscode/parser/hql -package org.springframework.ide.vscode.parser.hql
java -jar ${workdir}/antlr-${ANTLR_VERSION}.jar MySqlLexer.g4 MySqlParser.g4 -o ${workdir}/src/main/java/org/springframework/ide/vscode/parser/mysql -package org.springframework.ide.vscode.parser.mysql
java -jar ${workdir}/antlr-${ANTLR_VERSION}.jar SpelLexer.g4 SpelParser.g4 -o ${workdir}/src/main/java/org/springframework/ide/vscode/parser/spel -package org.springframework.ide.vscode.parser.spel
java -jar ${workdir}/antlr-${ANTLR_VERSION}.jar PostgreSqlLexer.g4 PostgreSqlParser.g4 -o ${workdir}/src/main/java/org/springframework/ide/vscode/parser/postgresql -package org.springframework.ide.vscode.parser.postgresql
java -jar ${workdir}/antlr-${ANTLR_VERSION}.jar CronLexer.g4 CronParser.g4 -o ${workdir}/src/main/java/org/springframework/ide/vscode/parser/cron -package org.springframework.ide.vscode.parser.cron
java -jar ${workdir}/antlr-${ANTLR_VERSION}.jar PropertyPlaceHolder.g4 -o ${workdir}/src/main/java/org/springframework/ide/vscode/parser/placeholder -package org.springframework.ide.vscode.parser.placeholder

