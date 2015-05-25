# Running integs

You'll need to set the path to your WildFly install, either by setting
`$JBOSS_HOME` or adding:

    :immutant {:test {:jboss-home "path/to/wildfly"}}

to your :user profile in `~/.lein/profiles.clj`.

You'll also need to ensure your WildFly install has a test user
setup. The easiest way to do that is by using the ci prep script:

    ../etc/bin/ci-prep-wildfly.sh path/to/wildfly/parent/dir 8.1.0.Final

Then, you can run all of the tests with `lein with-profile +integs  all`.
