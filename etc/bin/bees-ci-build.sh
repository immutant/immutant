#!/bin/bash

# This is the bees CI build. Any changes to the build script should be
# here instead if in the bees config.

set -e

BIN_DIR="${WORKSPACE}/bin"
WF_DIR="${WORKSPACE}/wildfly-dists"
WF8_VERSION="8.2.0.Final"
WF9_VERSION="9.0.0.Alpha1"
LEIN_VERSION=2.4.3
export PATH="${BIN_DIR}:${PATH}"
export WORKSPACE_HOME="${WORKSPACE}/home"
export LEIN_HOME="${WORKSPACE_HOME}/.lein"
export JVM_OPTS="-Dprogress.monitor=false"

function mark {
    echo
    echo "=============================================="
    echo $1
    date
    echo "=============================================="
    echo
}

rm -rf ${WORKSPACE}/target ${BIN_DIR}

mark "Installing leiningen ${LEIN_VERSION}"
mkdir -p ${BIN_DIR}
cd ${BIN_DIR}
wget --no-check-certificate https://raw.github.com/technomancy/leiningen/${LEIN_VERSION}/bin/lein
chmod +x lein
cd -

mark "Setting up lein profiles"
mkdir -p ${LEIN_HOME}
cp -f /private/projectodd/auth_profile.clj ${LEIN_HOME}/profiles.clj

rm -rf ${WF_DIR}

mark "Installing WildFly ${WF8_VERSION}"
etc/bin/ci-prep-wildfly.sh ${WF_DIR} ${WF8_VERSION}

mark "Installing WildFly ${WF9_VERSION}"
etc/bin/ci-prep-wildfly.sh ${WF_DIR} ${WF9_VERSION}

mark "Reversioning"
etc/bin/reversion.sh 2.x.incremental.${BUILD_NUMBER}

mark "Starting build + integ run against ${WF8_VERSION}"
export JBOSS_HOME="${WF_DIR}/wildfly-${WF8_VERSION}"
lein with-profile +integs modules all

mark "Starting integs with ${WF9_VERSION}"
export JBOSS_HOME="${WF_DIR}/wildfly-${WF9_VERSION}"
cd integration-tests
lein with-profile +integs all
cd -

mark "Starting deploy build"
lein with-profile +incremental modules deploy

mark "Starting doc build"
lein docs

mark "Done"
