#!/bin/bash

# This is the bees CI integ build. Any changes to the build
# script should be here instead if in the bees config.

set -e

DIR=$( cd "$( dirname "$0" )" && pwd )

. ${DIR}/common-build.sh

WF8_VERSION="8.2.0.Final"
WF9_VERSION="9.0.1.Final"

function install-wildfly {
    mark "Installing WildFly ${WF8_VERSION}"
    ${DIR}/ci-prep-as.sh ${AS_DIR} wildfly ${WF8_VERSION}

    mark "Installing WildFly ${WF9_VERSION}"
    ${DIR}/ci-prep-as.sh ${AS_DIR} wildfly ${WF9_VERSION}
}

cleanup
install-lein
setup-lein-profiles
install-wildfly

mark "Building SNAPSHOT without tests"
lein modules install

cd integration-tests

export JBOSS_HOME="${AS_DIR}/wildfly-${WF8_VERSION}"

mark "Starting integs with ${WF8_VERSION}"
lein with-profile +integs all

mark "Starting cluster tests with ${WF8_VERSION}"
lein with-profile +cluster all

export JBOSS_HOME="${AS_DIR}/wildfly-${WF9_VERSION}"

mark "Starting integs with ${WF9_VERSION}"
lein with-profile +integs all

mark "Starting cluster tests with ${WF9_VERSION}"
lein with-profile +cluster all

cd -

mark "Done"
