---
{:title "EAP"
 :sequence 5.5
 :description "Deploying your app to Red Hat JBoss Enterprise Application Platform"}
 ---

## HornetQ Exclusions

    :exclusions [org.hornetq/hornetq-jms-server org.hornetq/hornetq-server]
    :dependencies [[org.hornetq/hornetq-jms-server "2.3.25.Final"]
                   [org.hornetq/hornetq-server "2.3.25.Final"]]

## Narayana

    :exclusions [org.jboss.narayana.jta/narayana-jta]
    :dependencies [[org.jboss.jbossts.jta/narayana-jta "4.17.29.Final"]]

## Websockets

* jboss-web.xml
* config

## Infinispan issues

## Differences from WildFly

You should dissoc the :remote-type from the options passed to
`immutant.messaging/context` as EAP doesn't support WildFly's protocol
multiplexing over http.
