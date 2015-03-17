#!/bin/bash

# This is the bees CI build. Any changes to the build script should be
# here instead if in the bees config.

set -e

DIR=$( cd "$( dirname "$0" )" && pwd )

. ${DIR}/common-build.sh

java -version

cleanup
install-lein
setup-lein-profiles
install-wildfly

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
