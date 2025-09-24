#!/bin/bash
set -e

profiles=$1

../mvnw -B -f ../pom.xml -pl concourse-language-server -am clean install $profiles
