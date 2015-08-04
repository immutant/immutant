BIN_DIR="${WORKSPACE}/bin"
AS_DIR="${WORKSPACE}/as-dists"
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
    rm -rf ${AS_DIR}
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
