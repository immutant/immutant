#!/bin/bash

# This is the bees CI build. Any changes to the build script should be
# here instead if in the bees config.

set -e

BIN_DIR="${WORKSPACE}/bin"
WF_DIR="${WORKSPACE}/wildfly"
WF8_VERSION="8.1.0.Final"
WF9_VERSION="9.0.0.Alpha1"
export PATH="${BIN_DIR}:${PATH}"
export WORKSPACE_HOME="${WORKSPACE}/home"
export LEIN_HOME="${WORKSPACE_HOME}/.lein"
export JVM_OPTS="-Dprogress.monitor=false"

rm -rf ${WORKSPACE}/target ${BIN_DIR}

mkdir -p ${BIN_DIR}
cd ${BIN_DIR}
wget --no-check-certificate https://raw.github.com/technomancy/leiningen/2.4.3/bin/lein
chmod +x lein
cd -

mkdir -p ${LEIN_HOME}
cp -f /private/projectodd/auth_profile.clj ${LEIN_HOME}/profiles.clj

rm -rf ${WF_DIR}

etc/bin/ci-prep-wildfly.sh ${WF_DIR} ${WF8_VERSION}
etc/bin/ci-prep-wildfly.sh ${WF_DIR} ${WF9_VERSION}

etc/bin/reversion.sh 2.x.incremental.${BUILD_NUMBER}

export JBOSS_HOME="${WF_DIR}/wildfly-${WF8_VERSION}"
date
lein with-profile +integs modules all
date

export JBOSS_HOME="${WF_DIR}/wildfly-${WF9_VERSION}"
echo "Running integs with ${WF9_VERSION}"
cd integration-tests
lein with-profile +integs modules all
cd -

date
lein with-profile +incremental modules deploy
date
lein docs
date
