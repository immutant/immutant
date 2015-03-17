#!/bin/bash

# This is the bees CI cluster integ build. Any changes to the build
# script should be here instead if in the bees config.

set -e

DIR=$( cd "$( dirname "$0" )" && pwd )

. ${DIR}/common-build.sh

cleanup
install-lein
setup-lein-profiles
install-wildfly

mark "Starting cluster tests with ${WF8_VERSION}"
export JBOSS_HOME="${WF_DIR}/wildfly-${WF8_VERSION}"
cd integration-tests
lein with-profile +cluster all
cd -

mark "Starting cluster tests with ${WF9_VERSION}"
export JBOSS_HOME="${WF_DIR}/wildfly-${WF9_VERSION}"
cd integration-tests
lein with-profile +cluster all
cd -

mark "Done"
