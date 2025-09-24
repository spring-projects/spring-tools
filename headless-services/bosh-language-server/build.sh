#!/bin/bash
set -e

profiles=$1

../mvnw -B -f ../pom.xml -pl bosh-language-server -am clean install $profiles
