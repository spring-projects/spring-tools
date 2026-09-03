#!/bin/bash
set -e
workdir=`pwd`
cd ../headless-services
./mvnw clean install -Dmaven.test.skip=true

cd $workdir
./mvnw -Psnapshot -Pe441 clean install -Dmaven.test.skip=true -Declipse.p2.mirrors=false
