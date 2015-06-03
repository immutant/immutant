---
{:title "Messaging"
 :sequence 2
 :base-ns 'immutant.messaging
 :description "Simple creation and usage of distributed queues and topics"}
---

If you're coming from Immutant 1.x, you may notice that the messaging
artifact has been renamed (`org.immutant/immutant-messaging` is now
`org.immutant/messaging`), and the API has changed a bit. We'll point
out the notable API changes as we go.

## The API

The messaging [API] is backed by [HornetQ], which is an implementation
of [JMS]. JMS provides two primary destination types: *queues* and
*topics*. Queues represent point-to-point destinations, and topics
publish/subscribe.

To use a destination, we need to get a reference to one via the
[[queue]] or [[topic]] functions, depending on the type required. This
will create the destination if it does not already exist. This is a
bit different than the 1.x API, which provided a single `start`
function for this, and determined the type of destination based on
conventions around the provided name. In 2.x, we've removed those
naming conventions.

Once we have a reference to a destination, we can operate on it with
the following functions:

* [[publish]] - sends a message to the destination
* [[receive]] - receives a single message from the destination
* [[listen]] - registers a function to be called each time a message
  arrives at the destination

If the destination is a queue, we can do synchronous messaging
([request-response]):

* [[respond]] - registers a function that receives each request, and
  the returned value will be sent back to the requester
* [[request]] - sends a message to the responder

Finally, to deregister listeners, responders, and destinations, we
provide a single [[stop]] function. This is another difference
from 1.x - the `unlisten` and `stop` functions have been collapsed to
`stop`.

### Some Examples

You should follow the instructions in the [installation] guide to
set up a project using Immutant 2.x, and in addition to
`org.immutant/messaging` add `[cheshire "5.3.1"]` to the project
dependencies (we'll be encoding some messages as JSON in our examples
below, so we'll go ahead and add
[cheshire](https://github.com/dakrone/cheshire) while we're at it).
Then, fire up a REPL, and require the `immutant.messaging` namespace
to follow along:

```clojure
(require '[immutant.messaging :refer :all])
```

First, let's create a queue:

```clojure
(queue "my-queue")
```

That will create the queue in the HornetQ broker for us. We'll need a
reference to that queue to operate on it. Let's go ahead and store
that reference in a var:

```clojure
(def q (queue "my-queue"))
```

We can call `queue` any number of times - if the queue already exists,
we're just grabbing a reference to it.

Now, let's register a listener on our queue. Let's just print every
message we get:

```clojure
(def listener (listen q println))
```

We can publish to that queue, and see that the listener gets called:

```clojure
(publish q {:hi :there})
```

You'll notice that we're publishing a map there - we can publish
pretty much any data structure as a message. By default, that message
will be encoded using [edn]. We also support other encodings, namely:
`:fressian`, `:json`, and `:none`. We can choose a different encoding
by passing an :encoding option to `publish`:

```clojure
(publish q {:hi :there} :encoding :json)
```

If you want to use `:fressian` or `:json`, you'll need to add
`org.clojure/data.fressian` or `cheshire` to your dependencies to
enable them, respectively.

We passed our options to `publish` as keyword arguments, but they can
also be passed as a map:

```clojure
(publish q {:hi :there} {:encoding :json})
```

This holds true for any of the messaging functions that take options.

We're also passing the destination reference to `publish` instead of the
destination name. That's a departure from 1.x, where you could just pass the
destination name. Since we no longer have conventions about how queues and
topics should be named, we can no longer determine the type of the
destination from the name alone.

We can deregister the listener by either passing it to `stop` or
calling `.close` on it:

```clojure
(stop listener)
;; identical to
(.close listener)
```

Now let's take a look at synchronous messaging. Let's create a new
queue for this (you'll want to use a dedicated queue for each
responder) and register a responder that just increments the request:

```clojure
(def sync-q (queue "sync"))

(def responder (respond sync-q inc))
```

Then, we make a request, which returns a [Future] that we can
dereference:

```clojure
@(request sync-q 1)
```

The responder is just a fancy listener, and can be deregistered the
same way as a listener.

## Concurrency

Listeners can have multiple threads invoking their handler as messages
are consumed. You control how many with the `:concurrency` option
provided by [[listen]] and [[respond]]. By default, it is set to 1 for
topics and the number of available processors for queues, but for
IO-bound handlers, you may see better performance as you increase the
number. It very much depends on what your handler is doing and how
many messages it needs to process concurrently.

## Durable Topic Subscriptions

Typically, messages published to a topic are only delivered to
listeners connected to the topic at that time. But it's possible to
[[subscribe]] to a topic with a unique name, so that the broker will
accumulate messages for that client when it's disconnected and deliver
them in the proper order when the client reconnects.

Use the [[subscribe]] function to create a "durable topic subscriber".
Like [[listen]] it expects a callback function. Unlike [[listen]], the
destination *must* be a topic, and a unique `subscription-name` is
required. If the resulting client gets disconnected for any reason,
simply call [[subscribe]] again with the same `subscription-name` and
any messages published to the topic in the client's absence will be
mapped to the callback function.

Some additional server-side resources are required to track each
subscriber, of course, so [[unsubscribe]] is provided to tear down a
durable topic subscription when no longer needed.

## Contexts

Immutant borrows the `Context` abstraction introduced in [JMS] 2.0,
which is essentially a mashup of `Connection` and `Session`.

Most of the messaging functions accept a `:context` option. If
omitted, one is automatically created on the caller's behalf, used,
and then disposed of. This is fine for most use cases, but some will
require you to manage the lifecycle of one or more `Contexts`
yourself. Two cases, in particular:

* Remote destinations, discussed in the next section
* Publishing or receiving a batch of messages

When publishing a batch of messages, it's more efficient to create a
single [[context]] and pass it to each [[publish]] or [[request]]
call. Otherwise, a new one is created and torn down for every message
in the batch. Of course, you're responsible for closing any `Context`
you create so `with-open` is your friend:

```clojure
(with-open [ctx (context)]
  (let [q (queue "foo")]
    (dotimes [n 10000]
      (publish q n :context ctx))))
```

This is not a problem for [[listen]], [[subscribe]] or [[respond]]
since each only requires a single `Context` no matter how many times
their callback function is invoked. It is potentially an issue for
[[receive]], but if you're receiving a batch of messages, you should
consider using [[listen]] instead.

## Remote Destinations

To connect to a remote HornetQ instance, you'll need to create a
remote context (via the [[context]] function), and use it when getting
a reference to the destination:

```clojure
(with-open [context (context :host "some-host" :port 5445)]
  (publish
    (queue "foo" :context context)
    "a message"))
```

A few things to note about the above example:

* We're using `with-open` because you need to close any contexts you
  make.
* We don't need to pass the context to [[publish]], since it will
  reuse the context that was used to create the queue reference.

Most importantly, since we're passing the context to [[queue]], a
queue with that name must already exist on the remote host. When
[[queue]] is passed a remote context, it will return a reference to
the remote queue *without asking HornetQ to create it*.

## Destination creation in WildFly

Outside of WildFly, i.e. embedded in your standalone app, creating
destinations is as simple as calling either [[queue]] (or [[topic]])
without a remote context.

But creating destinations dynamically like that is not something
WildFly is built for. JEE apps expect their queues/topics to already
exist; creating them is the job of the server administrator.

Calling [[queue]] or [[topic]] within WildFly *might* work, but it
could also result in deadlock depending on the CPU resources available
and the number of apps being deployed simultaneously. You can avoid
that uncertainty by configuring the destinations yourself using the
normal WildFly mechanisms. You still have to call [[queue]] or
[[topic]] in your Clojure code, but because the named destinations
already exist, no deadlock will occur.

As
[this guide](https://docs.jboss.org/author/display/WFLY9/Messaging+configuration)
explains, there are 3 common ways to create your destinations with
WildFly: the main config file, one or more `-jms.xml` files, or the
CLI.

Here's a snippet from the main config file, e.g. `standalone-full.xml`
with comments showing the Clojure code required to reference each one:

```xml
...
  <hornetq-server>
    ...
    <jms-destinations>

      <!-- (queue "foo") -->
      <jms-queue name="foo">
        <entry name="java:/jms/queue/foo"/>
      </jms-queue>

      <!-- (topic "/foo/bar") -->
      <jms-topic name="/foo/bar">
        <entry name="java:/jms/topic/anything/really/foo/bar"/>
      </jms-topic>

    </jms-destinations>
  </hornetq-server>
</subsystem>
```

Note that while WildFly does require an `<entry>` element for each
destination, the Immutant client ignores it: the name you pass to
[[queue]] or [[topic]] directly corresponds to the `name` attribute of
`<jms-queue>` or `<jms-topic>`, respectively.

## Context modes

When creating a context, you can pass a `:mode` option that controls
how messages will be acknowledged and delivered.

Immutant provides three modes:

* `:auto-ack` - *the default for contexts*, when this mode is active,
  receipt of a message is automatically acknowledged when a `receive`
  call completes. This mode doesn't affect publication - `publish`
  calls will complete immediately.

* `:client-ack` - when this mode is active, you are responsible for
  acknowledging the message manually by calling `.acknowledge` on the
  Message object. This means you need to get the raw message (by
  passing `:decode? false` to `receive`). This mode doesn't affect
  publication - `publish` calls will complete immediately.

* `:transacted` - *the default for listeners*, when this mode is
  active, you are responsible for committing or rolling back (by
  calling `.commit` or `.rollback` on the context, respectively) any
  actions performed on the context. This applies to publishes *and*
  receives.

If a context is created with `:xa? true`, the `:mode` option is
ignored. See the [Transactions Guide] for more details.

## Context modes for listeners

The [[listen]] and [[respond]] functions take a `:mode` option, which
is used *instead* of the mode of any `:context` option (listeners need
a context per thread, so create and manage their own contexts). The
`:mode` option to these functions takes the same modes as above, with
the following behavioral differences (note that `:transacted` is the
default for listeners, *not* `:auto-ack`):

* `:auto-ack` - when active, the receipt of the message will
  automatically be acknowledged when the handler function completes.

* `:client-ack` - when active, you are responsible for
  acknowledging the message manually by calling `.acknowledge` on the
  Message object. This means you need to get the raw message (by
  passing `:decode? false` to `listen`).

* `:transacted` - when active, `.commit` is called on the context
  automatically if the handler function completes successfully. If it
  throws an exception, `.rollback` is called on the context. Any
  messaging operations that take a context will use the context that
  is active for the listener itself (if not passed one
  explicitly). This means that any messaging operations within the
  handler function are also transacted. This is the default mode for
  listeners.

If you need to use distributed transactions (XA) within a listener
function, you are responsible for demarcating the transaction. See the
[Transactions Guide] for more details.

## HornetQ configuration

When used outside of WildFly, we configure [HornetQ] via a pair of xml
files. If you need to adjust any of the HornetQ
[configuration options], you can provide a copy of one (or both) of
those files (`hornetq-configuration.xml` and `hornetq-jms.xml`, which
should be based off of the [default versions]) on your application's
classpath and your copies will be used instead of the default
ones. When making changes to these files, be careful about changing
existing settings, as Immutant relies on some of them.

We've also exposed a few HornetQ settings as system properties, namely:

| Property             | Description                                                 | Default           |
|----------------------|-------------------------------------------------------------|-------------------|
| `hornetq.data.dir`   | The base directory for HornetQ to store its data files      | `./hornetq-data/` |
| `hornetq.netty.port` | The port that HornetQ will listen on for remote connections | `5445`            |
| `hornetq.netty.host` | The host that HornetQ will listen on for remote connections | `localhost`       |

Note that any custom xml or system properties will be ignored when
running inside WildFly - you'll need to make adjustments to the
WildFly configuration to achieve similar effects.

In addition, it is possible to override many HornetQ configuration
settings at runtime using
[[immutant.messaging.hornetq/set-address-settings]].

## Transactions

When the messaging operations are left to create their own `Context`,
they check to see whether an XA transaction is active. If so, an XA
context is created and automatically enlisted as a resource in the
active transaction. Otherwise, a more efficient non-XA `Context` is
used.

So you only pay for transactions if you need them.

However, the default value for the [[context]] function's `:xa?`
option is `false`, so if you're managing `Context` instances yourself,
you must set `:xa?` to true if you need that `Context` to be part of a
distributed XA transaction.

### Listeners

In Immutant 1.x, message listeners were automatically enlisted
participants in an XA transaction, but that is not the case with
Immutant 2.x. Within the listener function, you must now explicitly
define a transaction using one of the macros in
[[immutant.transactions]]. If an exception escapes its body, the
transaction will be rolled back, and if the exception bubbles out of
the listener function, the message will be queued for redelivery.

But the rollback of the transaction has no relationship to message
redelivery, which is only triggered by the exception. The transaction
*could* be rolled back as a result of calling
[[immutant.transactions/set-rollback-only]], in which case no
exception would be thrown. Hence, rollback would occur, but not
redelivery.

See the [Transactions Guide] for more details.

[HornetQ]: http://hornetq.jboss.org/
[API]: immutant.messaging.html
[JMS]: https://en.wikipedia.org/wiki/Java_Message_Service
[installation]: guide-installation.html
[request-response]: https://en.wikipedia.org/wiki/Request-response
[Future]: http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/Future.html
[edn]: https://github.com/edn-format/edn
[default versions]: https://github.com/projectodd/wunderboss/blob/{{wunderboss-tag}}/modules/messaging-hornetq/src/main/resources/
[configuration options]: https://docs.jboss.org/hornetq/2.4.0.Final/docs/user-manual/html_single/#server.configuration
[Transactions Guide]: guide-transactions.html
