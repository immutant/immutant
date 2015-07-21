BIN_DIR="${WORKSPACE}/bin"
WF_DIR="${WORKSPACE}/wildfly-dists"
WF8_VERSION="8.2.0.Final"
WF9_VERSION="9.0.0.Final"
LEIN_VERSION=2.5.1
export PATH="${BIN_DIR}:${PATH}"
export WORKSPACE_HOME="${WORKSPACE}/home"
export LEIN_HOME="${WORKSPACE_HOME}/.lein"
export JVM_OPTS="-Dprogress.monitor=false"

DIR=$( cd "$( dirname "$0" )" && pwd )

function mark {
    echo
    echo "=============================================="
    echo $1
    date
    echo "=============================================="
    echo
}

function cleanup {
    rm -rf ${WORKSPACE}/target
    rm -rf ${BIN_DIR}
    rm -rf ${WF_DIR}
}

function install-lein {
    mark "Installing leiningen ${LEIN_VERSION}"
    mkdir -p ${BIN_DIR}
    cd ${BIN_DIR}
    wget --no-check-certificate https://raw.github.com/technomancy/leiningen/${LEIN_VERSION}/bin/lein
    chmod +x lein
    cd -
}

function setup-lein-profiles {
    mark "Setting up lein profiles"
    mkdir -p ${LEIN_HOME}
    cp -f /private/projectodd/auth_profile.clj ${LEIN_HOME}/profiles.clj
}

function install-wildfly {
    mark "Installing WildFly ${WF8_VERSION}"
    ${DIR}/ci-prep-wildfly.sh ${WF_DIR} ${WF8_VERSION}

    mark "Installing WildFly ${WF9_VERSION}"
    ${DIR}/ci-prep-wildfly.sh ${WF_DIR} ${WF9_VERSION}
}
