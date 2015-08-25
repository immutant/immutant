---
{:title "EAP"
 :sequence 5.5
 :description "Deploying your app to Red Hat JBoss Enterprise Application Platform"}
 ---

[Red Hat JBoss Enterprise Application Platform](https://www.redhat.com/en/technologies/jboss-middleware/application-platform)
(EAP for short) is the commercial product built on the open source
JBoss application server. The current version (6.4.0) is based on a
JBoss AS version that predates WildFly, so has some component
differences with WildFly and Immutant itself. However, starting with
Immutant 2.1.0, you can now deploy Immutant applications to EAP, with
some caveats.

## Messaging

EAP 6.4.0 uses an older version of HornetQ (2.3.25 vs. 2.4.5 brought
in by Immutant). If you are using `org.immutant/messaging`, you'll
need to exclude its version of HornetQ and bring in the version being
used in EAP:

    :exclusions [org.hornetq/hornetq-jms-server org.hornetq/hornetq-server]
    :dependencies [[org.hornetq/hornetq-jms-server "2.3.25.Final"]
                   [org.hornetq/hornetq-server "2.3.25.Final"]]


## Transactions

EAP 6.4.0 uses a version of Narayana (4.17.29 vs. 5.0.3 brought in by
Immutant). If you are using `org.immutant/transactions`, you'll need
to exclude its version of Narayana and bring in the version being used
in EAP (note the different groupId):

    :exclusions [org.jboss.narayana.jta/narayana-jta]
    :dependencies [[org.jboss.jbossts.jta/narayana-jta "4.17.29.Final"]]

If you are connecting external clients to the HornetQ inside EAP, you
must *not* set the `:remote-type` option to `:hornetq-wildfly` in the
options passed to [[immutant.messaging/context]] and will need to set
the `:port` option to `5445` as EAP doesn't support WildFly's protocol
multiplexing over HTTP.

## Web

EAP 6.4.0 uses [jbossweb] as its web/servlet server instead of
[Undertow] (which Immutant uses standalone, and is the web/servlet
server inside WildFly). The behavior of web requests in EAP should be
the same as WildFly, but you'll need to do a couple of extra
configuration steps if you intend to use Websockets.

First, you need to change the configuration of EAP itself (in
`standalone/configuration/standalone*.xml`) to use a different
protocol in the web subsystem. To do so, change:

    <connector name="http" protocol="HTTP/1.1" scheme="http" socket-binding="http"/>

to

    <connector name="http" protocol="org.apache.coyote.http11.Http11NioProtocol" scheme="http" socket-binding="http"/>


Second, you'll need to include a custom `jboss-web.xml` file in the
generated WAR file that enables Websockets for the deployment. The
file needs to be under `WEB-INF/jboss-web.xml`, and contain:

    <jboss-web>
      <enable-websockets>true</enable-websockets>
    </jboss-web>

The easiest way to do this is to use the [`:war-resources` option of
the `lein-immutant` plugin]. Note that a WAR with the above contents
*can't* be deployed to a WildFly server, as `enable-websockets` isn't
a valid option there, unfortunately.

## Infinispan issues

What goes here?


## Detecting EAP at runtime

If you need to detect if you are running inside EAP or not, in
addition to the [[immutant.util/in-container?]] function to let you
know if you are in any container (WildFly or EAP), there is
[[immutant.util/in-eap?]], which will only return `true` if you are
running inside EAP.

## Doing it all from a profile
