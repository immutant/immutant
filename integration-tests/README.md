# Running integs

You'll need to set the path to your WildFly install, either by setting
`$JBOSS_HOME` or adding:

    :immutant {:test {:jboss-home "path/to/wildfly"}}

to your :user profile in `~/.lein/profiles.clj`.

Then, you can run all of the tests with `lein modules all`, or an
individual test with `lein all` within the test project itself.
