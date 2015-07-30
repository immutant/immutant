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

mark "Reversioning"
etc/bin/reversion.sh 2.x.incremental.${BUILD_NUMBER}

mark "Starting build"
lein modules all

mark "Testing messaging with HornetQ 2.3"
cd messaging
lein with-profile +hornetq-2.3 test
cd -

mark "Starting deploy build"
lein with-profile +incremental modules deploy

mark "Starting doc build"
lein docs

mark "Done"
