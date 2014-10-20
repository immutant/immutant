#! /bin/sh

dest_dir=$1
version=$2
jboss_home="${dest_dir}/wildfly-${version}"

if [ -z "${version}" ]; then
  echo "Usage: ${0} dest-dir wildfly-version"
  exit 1
fi

if [ ! -d ${jboss_home} ]; then
  echo "Installing WildFly ${version} to ${dest_dir}"
  cd ${dest_dir}
  wget http://download.jboss.org/wildfly/${version}/wildfly-${version}.tar.gz
  tar xf wildfly-${version}.tar.gz
  cd -
fi

# TODO: domain mode
conf="${jboss_home}/standalone/configuration/standalone-full.xml"
if [ $(grep -c NIO ${conf}) -eq 0 ]; then
  echo "Enabling NIO journal"
  sed -i.bak "s/<hornetq-server>/<hornetq-server><journal-type>NIO<\/journal-type>/" ${conf}
  echo "Enabling TRACE logging"
  sed -i.bak '/<root-logger>/{N; s/<root-logger>.*<level name="INFO"/<root-logger><level name="TRACE"/g}' ${conf}
  echo "Adding application user testuser:testuser"
  ${jboss_home}/bin/add-user.sh --silent -a -u 'testuser' -p 'testuser' -g 'guest'
fi
