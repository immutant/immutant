#! /bin/sh

dest_dir=$1
type=$2
version=$3
jboss_home="${dest_dir}/${type}-${version}"

if [ -z "${version}" ]; then
    echo "Usage: ${0} dest-dir (wildfly|EAP) version"
    exit 1
fi

if [ ! -d ${jboss_home} ]; then
    mkdir -p ${dest_dir}
    cd ${dest_dir}
    echo "Installing ${type} ${version} to ${dest_dir}"
    if [ "$type" = "wildfly" ]; then
        wget -nv http://download.jboss.org/wildfly/${version}/wildfly-${version}.tar.gz
        tar xf wildfly-${version}.tar.gz
        cd -
    else
        if [ "x${EAP_ARCHIVE_DIR}" = "x" ]; then
            echo "Error: EAP_ARCHIVE_DIR not set"
            exit 1
        fi
        tar xf ${EAP_ARCHIVE_DIR}/EAP-${version}.tar.gz
    fi
    cd -
fi

conf="${jboss_home}/standalone/configuration/standalone-full.xml"
if [ $(grep -c NIO ${conf}) -eq 0 ]; then
    echo "Enabling NIO journal to avoid AIO failures"
    perl -p -i -e "s:(<hornetq-server>)$:\1<journal-type>NIO</journal-type>:" $(ls ${jboss_home}/*/configuration/*)
fi

#echo "Enabling TRACE logging"
#sed -i.bak '/<root-logger>/{N; s/<root-logger>.*<level name="INFO"/<root-logger><level name="TRACE"/g}' ${conf}
echo "Adding application user testuser:testuser1!"
${jboss_home}/bin/add-user.sh --silent -a -u 'testuser' -p 'testuser1!' -g 'guest'

# see https://github.com/jboss-developer/jboss-eap-quickstarts/tree/6.4.x/websocket-hello#review-the-modified-server-configuration
if [ "$type" = "EAP" ]; then
    echo "Setting EAP connector type for websockets"
    perl -p -i -e "s:HTTP/1.1:org.apache.coyote.http11.Http11NioProtocol:" $(ls ${jboss_home}/*/configuration/*)
fi
