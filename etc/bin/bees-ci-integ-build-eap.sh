#!/bin/bash

# This is the bees CI integ build. Any changes to the build
# script should be here instead if in the bees config.

set -e

DIR=$( cd "$( dirname "$0" )" && pwd )

. ${DIR}/common-build.sh

EAP6_VERSION=6.4.0
EAP7_VERSION=7.0.0

function install-eap {
    mark "Installing EAP $1"
    ${DIR}/ci-prep-as.sh ${AS_DIR} EAP $1
}

function run-tests {

    export JBOSS_HOME="${AS_DIR}/EAP-$1"

    install-eap $1

    cd integration-tests

    mark "Starting integs"
    lein with-profile $2 all

    mark "Starting cluster tests"
    lein with-profile $3 all

    cd -
}

cleanup
install-lein
setup-lein-profiles

mark "Building SNAPSHOT without tests"
lein modules install

export VERBOSE=1
export FLOG=1

run-tests ${EAP6_VERSION} "+eap"    "+cluster,+eap-base"
run-tests ${EAP7_VERSION} "+integs" "+cluster"

mark "Done"
