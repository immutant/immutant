---
{:title "Logging"
 :sequence 6
 :description "Details about how logging with Immutant works, along with how to change it"}
---

Logging with Java is a many-headed beast, and the behavior and
configuration of logging for an Immutant-based application depends on
how you run it. When an application is launched outside of a [WildFly]
container, using and configuring logging is fairly
straightforward. Inside of WildFly, there are a few hoops you'll have
to jump through if you can't use the default logging configuration.

## Logging when outside of WildFly

When used outside of WildFly, Immutant uses [SLF4J] and [Logback] for
logging by default. Any log messages that Immutant itself generates
will be handled by Logback, as will anything your app logs via
[clojure.tools.logging] \(or [Timbre], if you configure it to delegate to
clojure.tools.logging).

You can adjust the root logging level at runtime by calling
[[immutant.util/set-log-level!]]. **Note**: this will only work if
Logback is actually being used, and not replaced with another
implementation as we discuss below.

If the default logging configuration doesn't meet your needs, you can
provide an alternate Logback configuration, or replace Logback with
some other SLF4J provider.

### The default Logback configuration

By default, Logback is configured to log at `INFO` and below to the
console, with some of the chattier libraries we bring in configured at
`WARN` and below. The output looks like:

    23:58:53.313 INFO  [my-app.core] (main) an info message
    23:58:53.313 WARN  [my-app.core] (main) a warning message
    23:58:53.313 ERROR [my-app.core] (main) an error message
    23:58:53.450 INFO  [org.projectodd.wunderboss.web.Web] (main) Registered web context /

The [default configuration] is only applied when Logback is available
and you don't provide an overriding configuration in your application.

### Overriding the default configuration

If you want a different format for your log messages, or perhaps send
them to a file, or configure any of the other [myriad options], you
can either provide a [logback.xml] file on the classpath of your app
(e.g. inside `resources/`), or set the `logback.configurationFile`
system property to the path of the file or resource if you're using
something other than `logback.xml`.

When defining a custom configuration, it may be useful to use the
[default configuration] as a starting point.

### Replacing Logback

If you want to use a logging implementation other than Logback, you'll
need to exclude Logback and bring in your preferred implementation
along with the related SLF4J bridge. For example, to use [Log4j], you
can modify your `:dependencies` like so:

```clojure

  :dependencies [...
                 [org.immutant/immutant "2.x.incremental.284"
                   :exclusions [ch.qos.logback/logback-classic]]
                 [org.apache.logging.log4j/log4j-core "2.0.2"]
                 [org.apache.logging.log4j/log4j-slf4j-impl "2.0.2"]]
```

For more information on using other logging implementations with
SLF4J, see the [SLF4J manual].

## Logging when inside WildFly

When used inside of WildFly, jboss-logging is activated and replaces
Logback as the logging implementation. The default configuration for
it produces output that is very similar to the default Logback output
above, and it is written to the console and to
`$WILDFLY_HOME/standalone/log/server.log`. Any log messages that
Immutant itself generates will be handled by jboss-logging, as will
anything your app logs via [clojure.tools.logging] \(or [Timbre], if
you configure it to delegate to clojure.tools.logging) or writes to
stdout/stderr.

If you need to alter the default logging configuration, you have three options:

* Modify the logging configuration in WildFly's `standalone-*.xml` files.
* Disable the logging subsystem, which re-enables Logback, and provide
  a custom `logback.xml`.
* Disable the logging subsystem *and* Logback, and bring in a
  different SLF4J logging implementation.

### Modifying the WildFly logging configuration

WildFly provides a very sophisticated logging system that nobody
completely understands. It's possible to configure hierarchical,
categorized log message routing, complex file rotation, syslog
integration, SMTP notifications, SNMP traps, JMS, JMX and much
more. Obviously, most of that is far beyond the scope of this
document. Instead, we refer you to the WildFly
[logging documentation].

### Disabling the logging subsystem in WildFly

In order disable the logging subsystem in WildFly, you'll need
to do a little bit of work.

First, you'll need to modify the `jboss-deployment-structure.xml` file
that gets placed in the war's `WEB-INF/` directory. To get it, run:

    lein immutant war

In addition to generating a war file, this also dumps a copy of the
default `jboss-deployment-structure.xml` to `target/`. Grab that and
copy it to `war-resources/WEB-INF/` in the root of your
application. Then, add the following to it:

    <exclude-subsystems>
      <subsystem name="logging" />
    </exclude-subsystems>

Second, in order for your custom resources to be included in the war
file, you'll need to add the following to your `project.clj`:

```clojure
  :immutant {:war {:resource-paths ["war-resources"]}}
```

Once you disable the logging subsystem, you can now provide a custom
`logback.xml` as we discussed above, with one important difference -
the `logback.xml` must reside in the war file instead of simply on the
application's classpath. Therefore, you'll also need to put your
`logback.xml` in `war-resources/WEB-INF/lib/`.

You can also still provide an alternate SLF4J implementation as we did
above, but any configuration for it (`log4j.xml`, etc.), will need to
be in `WEB-INF/lib` as well.

Now, regenerate your war file, and you should be good to go. For more
information on running your application in WildFly, see our
[WildFly guide].

[SLF4J]: http://slf4j.org/
[Logback]: http://logback.qos.ch/
[clojure.tools.logging]: https://github.com/clojure/tools.logging
[Timbre]: https://github.com/ptaoussanis/timbre
[myriad options]: http://logback.qos.ch/manual/index.html
[logback.xml]: http://logback.qos.ch/manual/configuration.html
[default configuration]: https://github.com/projectodd/wunderboss/blob/master/modules/core/src/main/resources/logback-default.xml
[incremental build]: http://immutant.org/builds/2x/
[logging documentation]: https://docs.jboss.org/author/display/WFLY8/Logging+Configuration
[WildFly]: http://wildfly.org/
[Log4j]: http://logging.apache.org/log4j/2.x/
[SLF4J manual]: http://www.slf4j.org/manual.html#swapping
[WildFly guide]: guide-wildfly.html
