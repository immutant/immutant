# Running integs

You'll need to set the path to your WildFly install, either by setting
`$JBOSS_HOME` or adding:

    :immutant {:test {:jboss-home "path/to/wildfly"}}

to your :user profile in `~/.lein/profiles.clj`.

You'll also need to ensure your WildFly install has a test user
setup. The easiest way to do that is by using the ci prep script:

    ../etc/bin/ci-prep-as.sh path/to/wildfly/parent/dir wildfly 9.0.1.Final

To run all of the tests for WildFly or EAP 7.x:

    lein with-profile +integs all
    lein with-profile +cluster all

EAP 6.4 is supported, but only by using special profiles:

    lein with-profile +eap all
    lein with-profile +cluster,+eap-base all
