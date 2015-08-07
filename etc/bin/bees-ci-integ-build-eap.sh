#!/bin/bash

# This is the bees CI integ build. Any changes to the build
# script should be here instead if in the bees config.

set -e

DIR=$( cd "$( dirname "$0" )" && pwd )

. ${DIR}/common-build.sh

EAP_VERSION=6.4.0

function install-eap {
    mark "Installing EAP ${EAP_VERSION}"
    ${DIR}/ci-prep-as.sh ${AS_DIR} EAP ${EAP_VERSION}
}

cleanup
install-lein
setup-lein-profiles
install-eap

mark "Building SNAPSHOT without tests"
lein modules install

cd integration-tests

export JBOSS_HOME="${AS_DIR}/EAP-${EAP_VERSION}"

mark "Starting integs"
lein with-profile +eap all

mark "Starting cluster tests"
lein with-profile +cluster,+eap-base all

cd -

mark "Done"
