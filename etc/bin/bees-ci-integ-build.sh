#!/bin/bash

# This is the bees CI integ build. Any changes to the build
# script should be here instead if in the bees config.

set -e

DIR=$( cd "$( dirname "$0" )" && pwd )

. ${DIR}/common-build.sh

WF8_VERSION="8.2.1.Final"
WF9_VERSION="9.0.2.Final"
WF10_VERSION="10.1.0.Final"

function install-wildfly {
    mark "Installing WildFly $1"
    ${DIR}/ci-prep-as.sh ${AS_DIR} wildfly $1
}

function run-tests {
    export JBOSS_HOME="${AS_DIR}/wildfly-$1"

    install-wildfly $1

    cd integration-tests

    mark "Starting integs with $1"
    lein with-profile +integs all

    mark "Starting cluster tests with $1"
    lein with-profile +cluster all

    cd -
}

cleanup
install-lein
setup-lein-profiles

mark "Building SNAPSHOT without tests"
lein modules install

run-tests ${WF8_VERSION}
run-tests ${WF9_VERSION}
run-tests ${WF10_VERSION}

mark "Done"
