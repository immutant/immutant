# Immutant

Immutant is an application server for Clojure based off of
JBossAS/WildFly. This README covers building Immutant. 
For usage and more details, see http://immutant.org/. 

To file an issue, see https://issues.jboss.org/browse/IMMUTANT

## Requirements

* Maven 3 or Leiningen 2.3.4+
* Configuration of the JBoss Maven repository in settings.xml


## Dependencies

Immutant depends on polyglot: https://github.com/projectodd/jboss-polyglot

Polyglot is published as a incremantal builds from our CI server. If
you need to make changes to polyglot, you'll need to build it
separately and adjust the `version.polyglot` property in Immutant's
`pom.xml` to use that local snapshot.

## Building

Install the project using the provided settings.xml:

    mvn -s support/settings.xml install

If you will be building the project often, you'll want to
create/modify your own `~/.m2/settings.xml` file.

If you're a regular JBoss developer, see:

* http://community.jboss.org/wiki/MavenGettingStarted-Developers

Otherwise, see: 

* http://community.jboss.org/wiki/MavenGettingStarted-Users

Once your repositories are configured, simply type:

    mvn install

### Building with Leiningen

You can optionally build the Immutant source with Leiningen. But you
must install the `build-support` plugin first:

    cd support/build-support
    lein install

Once installed, cd back to the root of the project and run the
following:

    lein modules all

The `modules` higher-order task will run the task passed to it (e.g.
the `all` alias) in all that project's child modules. If you cd inside
one of the child modules, you can just run lein as you normally would,
but you should run the above at least once so that interdependent
modules are installed in your local repo.

## Testing 

All unit tests will be run during the build process, but tests can be
run independently with the following command:

    mvn -s support/settings.xml test

The integration tests (a.k.a. integs), which are not run as part of
the main build, can be run like this:

    cd integration-tests
    mvn test -s ../support/settings.xml

Or a single integration test can be run like this:

    mvn test -s ../support/settings.xml -Dns=namespace-of-test

where 'namespace-of-test' is the portion of the ns without the
'immutant.integs' prefix.

If you wish to skip the unit tests during the build process (to speed
things up) you can add the `-Dmaven.test.skip=true` option when
running the `mvn install` command.

## Running

To run your local build, set `IMMUTANT_HOME` and the lein-immutant
plugin will use it:

    export IMMUTANT_HOME=/path/to/immutant/build/assembly/target/stage/immutant/


## License

Immutant is licensed under the GNU Lesser General Public License v3.
See [licenses/LICENSE-LGPL.txt](licenses/LICENSE-LGPL.txt) for details.


