dest_dir=$1
version=$2
jboss_home="${dest_dir}/wildfly-${version}"

if [ ! -d ${jboss_home} ]; then
  cd ${dest_dir}
  wget http://download.jboss.org/wildfly/${version}/wildfly-${version}.tar.gz
  tar xf wildfly-${version}.tar.gz
  cd -
fi

conf="${jboss_home}/standalone/configuration/standalone-full.xml"
if [ $(grep -c NIO  ${conf}) -eq 0 ]; then
  echo "Enabling NIO journal"
  sed -i.bak "s/<hornetq-server>/<hornetq-server><journal-type>NIO<\/journal-type>/" ${conf}
  # TODO: domain mode ^
  echo "Adding application user testuser:testuser"
  ${jboss_home}/bin/add-user.sh -a -u 'testuser' -p 'testuser' -g 'guest'
fi
