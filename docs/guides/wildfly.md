---
{:title "WildFly"
 :sequence 5
 :description "Deploying your app to WildFly"}
---

One of [the primary goals for The Deuce](/news/2014/04/02/the-deuce/)
was the removal of the ancient AS7 fork we lugged around in
Immutant 1.x. This eliminates the need to install and deploy your apps
into a "container" to use the Immutant libraries.

But here's the trade-off: app server containers can simplify the
configuration of features -- e.g. security, monitoring, clustering --
for all the applications deployed to it.

And since each Immutant library automatically benefits in some way
from being clustered, we wanted to *facilitate* app server deployment
but not actually *require* it. Further, we didn't want to require any
tweaking of the stock "vanilla" configuration provided by the app
server. This meant using the standard deployment protocol for all Java
app servers: war files.

Theoretically, this implies you could stick the Immutant jars in any
ol' war file and deploy them to any ol' Java app server.
Unfortunately, Immutant's ability to work both outside and inside a
container requires some "glue code" that must be aware of the
container's implementation.

For this reason, Immutant intentionally uses the same services as
[WildFly], the community-supported upstream project for the
commercially-supported [JBoss EAP] product. And these are the
containers we'll initially support.

## WildFly

There are lots of resources for installing and administering WildFly,
and frankly, we love being able to refer you to those rather than
write them ourselves. :)

Thankfully, installing WildFly is trivial:

    $ wget http://download.jboss.org/wildfly/8.1.0.Final/wildfly-8.1.0.Final.zip
    $ unzip wildfly-8.1.0.Final.zip

Downloading and unpacking it somewhere are all there is to it. Running
it is easy, too:

    $ wildfly-8.1.0.Final/bin/standalone.sh

Pass it `-h` to see what options it supports. The main one you'll use
is `-c` which refers to one of its config files beneath
`standalone/configuration`. The default config doesn't include
HornetQ, for example, so to use `immutant.messaging`, you'll need to
start WildFly as follows:

    $ wildfly-8.1.0.Final/bin/standalone.sh -c standalone-full.xml

And if you want clustering...

    $ wildfly-8.1.0.Final/bin/standalone.sh -c standalone-full-ha.xml

You can create your own, of course, too.

## The lein-immutant plugin

The [lein-immutant] plugin was fundamental to developing apps for
Immutant 1.x. In *The Deuce*, it's only required if you wish to deploy
your Clojure apps to WildFly, and its formerly numerous tasks have
been reduced to two: `immutant war` and `immutant test`. Add the
latest version to the `:plugins` section of your `project.clj` to
install it, e.g.

    :plugins [[lein-immutant "2.0.0-alpha1"]]

### Creating a war file

Immutant war files require a bit of special config: a couple of jars
of the aforementioned "glue code", a properties file to trigger that
code, a couple of tags in `web.xml`, and a
`jboss-deployment-structure.xml` to link the deployment to the
necessary WildFly modules.  You'll find copies of the latter two files
beneath `target/` after you run the war task in your project, along
with an *uberjar* containing your app plus its dependencies, and
finally a war file packaging it all up together:

    $ lein immutant war

The `immutant war` task provides a number of configuration options,
all of which can be specified both in the `[:immutant :war]` path of
`project.clj` and as command line arguments, with the latter taking
precedence.

For a detailed description of each option:

    $ lein help immutant deployment

For a brief listing of just the command line switches:

    $ lein help immutant war

### Running tests in-container

Although you no longer need to run a container to test your
applications' use of the Immutant libraries, it is still possible via
the `immutant test` task.

    $ lein immutant test -j /srv/wildfly

It will find all the tests (or [Midje] facts, or [Expectations]
expectations) in a project, fire up the [WildFly] instance installed
at `/srv/wildfly`, deploy the project to it, connect to its REPL, run
all the tests, undeploy the app, shutdown the WildFly process, and
display the results, returning success only if all tests pass.

Because it conveniently runs all your tests inside the app server, a
successful run yields a high confidence that your code will run
correctly when it counts – in production, when deployed to the same
app server. For this reason, it may also be useful to run it on your
app's Continuous Integration host whenever any changes are committed.

The server config and log output for the WildFly instance used for the
test run can be found beneath your project's
`target/isolated-wildfly/` directory.

Similar to the `immutant war` task, configuration of the `immutant
test` task may be specified in either the `[:immutant :test]` path of
`project.clj` or as command line arguments. For a detailed description
of each option:

    $ lein help immutant testing

And for a brief listing of just the command line switches:

    $ lein help immutant test

## Deploying to WildFly

Once you have a war file, it's a simple matter of making it known to
your WildFly server. The easiest way to do that is to copy it to a
directory that is monitored by WildFly for artifacts to deploy.
Assuming you installed WildFly in `/srv/wildfly`, that path is
`/srv/wildfly/standalone/deployments`. For example:

    $ lein immutant war
    $ cp target/myapp.war /srv/wildfly/standalone/deployments

Alternatively,

    $ lein immutant war -o /srv/wildfly

If not already running, fire up WildFly to see your deployed app:

    $ /srv/wildfly/bin/standalone.sh -c standalone-full.xml

## Running Ring Handlers in WildFly

When running inside WildFly, the `:host` and `:port` options to
`immutant.web/run` are silently ignored since your handlers are
mounted on WildFly's internal Undertow server, bound to whatever
host/port it's been configured for.

Also, the URL for your handler will include a context path
corresponding to the base name of your deployed war file. This context
path will prefix whatever `:path` option you specified. To override
this and set your context path to "/" instead, name your war file
`ROOT.war`:

    $ lein immutant war -o /srv/wildfly -n ROOT

## Logging in WildFly

To learn about how logging works when inside WildFly, along with how
to change the default configuration, see our [logging guide].

[WildFly]: http://wildfly.org
[JBoss EAP]: http://www.jboss.org/products/eap/overview/
[lein-immutant]: https://github.com/immutant/lein-immutant/tree/2x-dev
[Midje]: https://github.com/marick/Midje
[Expectations]: http://jayfields.com/expectations/
[logging guide]: guide-logging.html
