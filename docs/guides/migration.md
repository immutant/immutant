---
{:title "Migration"
 :sequence 0.1
 :toc false
 :description "Immutant 1.1 -> 2.0 migration guide"}
---

This currently tracks changes between the 1.1 and 2.0 API, and will
eventually be a more thorough migration guide.

Structure: each section covers a namespace. If the namespace has been
renamed, that will be reflected by old namespace -> new namespace. If
the namespace has yet to be ported (but probably will, it's marked
with "-> ?". If it is now gone, it's marked with "REMOVED".

This list includes all of the Immutant namespaces, some of which
were/are for internal use only.


## immutant.cache -> [immutant.caching](immutant.caching.html)

The `Mutable` interface is gone. To put something in an immutant
cache, you can use either the new `immutant.caching/compare-and-swap!`
or java interop (`org.infinispan.Cache` extends `ConcurrentMap` and
therefore `Map`). To insert entries with various ttl/idle values, use
the new `with-expiration` function.

Both `lookup` and `create` have been collapsed into `cache`, which
behaves like the old `lookup-or-create`. To force the creation of an
existing cache, you must `stop` the running one. Otherwise, `cache`
will just return it.

Caches are no longer transactional by default, and if set to be so,
require a locking mode to be selected (optimistic by default).

`core.cache` and `core.memoize` are no longer transitive dependencies
of `immutant.caching`. To extend an immutant cache to core's
`CacheProtocol`, add `org.clojure/core.cache` to your project's deps
and require `immutant.caching.core-cache`.

The `memo` function has been moved to `immutant.caching.core-memoize`
which you should only require after adding `org.clojure/core.memoize`
to your project's deps.

Some option keys and values have changed:

  - `:sync` has been collapsed into `:mode` to match
    `org.infinispan.configuration.cache/CacheMode`, so the possible
    values of `:mode` are now:
    - `:local`
    - `:repl-sync`
    - `:repl-async`
    - `:invalidation-sync`
    - `:invalidation-async`
    - `:dist-sync`
    - `:dist-async`
  - `:tx` -> `:transactional`
  - `:encoding` is gone, replaced with the `with-codec` fn
  - `:seed` is gone
  - `:config` is now `:configuration`

### immutant.cache.config REMOVED
### immutant.cache.core REMOVED
### immutant.cache.wrapper REMOVED

## [immutant.codecs](immutant.codecs.html)

`:text` codec was removed. The default supplied codecs in 2.x are:
`:none`, `:edn`, and `:json`. `:fressian` can be enabled by calling
`immutant.codecs.fressian/register-fressian-codec`.

## [immutant.daemons](immutant.daemons.html)

Now resides in
[org.immutant/core](https://clojars.org/org.immutant/core), with a
slightly simpler interface.

## immutant.dev REMOVED

Used for dev inside the container, but you can get all these same
facilities with standard tools outside of the container with 2.x, and
we're no longer exposing the project map, so this wouldn't be very
useful inside the container with 2.x.

## immutant.jobs -> [immutant.scheduling](immutant.scheduling.html)

The API is similar. `schedule` now takes a map or kwargs, and there
are now helpers for each option that help you generate that map. A
cronspec is no longer a top-level arg, but instead is specified in the
map using the `:cron` key. A name is no longer required, but an
optional id can be provided to allow you to reschedule the job.

The `set-scheduler-options` is now handled by passing additional
options to `schedule`. If different scheduler options are given on
different schedule calls, new schedulers are created.

If you need access to the raw quartz scheduler, use `(.scheduler
(immutant.scheduling.internal/scheduler opts)`. (Maybe we should
restore `internal-scheduler`?)

### immutant.jobs.internal -> immutant.scheduling.internal

## immutant.logging REMOVED

## [immutant.messaging](immutant.messaging.html)

Has a similar API, except around destination creation and passing. Fns
now take destination objects instead of strings, and the destination
objects must be created via `queue` and `topic`. Connections and
sessions have been replaced with contexts, available from `context`.

`unlisten` and `stop` have been merged in to `stop`. `message-seq` is
no more.

### immutant.messaging.codecs

Brought over with few changes.

### immutant.messaging.core REMOVED

Merged with `immutant.messaging.internal`.

### [immutant.messaging.hornetq](immutant.messaging.hornetq.html)

Brought over with a few changes.

### immutant.messaging.internal

Brought over, but with a drastically different API.

## immutant.pipeline -> [immutant.messaging.pipeline](immutant.messaging.pipeline.html)

The API is unchanged, other than renaming the namespace.

## immutant.registry REMOVED

## immutant.repl -> immutant.wildfly.repl

Still there, but with a different API. It's now only used inside the
container.

## immutant.resource-util REMOVED

## immutant.runtime REMOVED

### immutant.runtime.bootstrap REMOVED

## immutant.runtime-util REMOVED

## immutant.util

Split across three namespaces:

* [immutant.util](immutant.util.html) - fns appropriate for app use
* immutant.internal.util - fns used by Immutant itself, and not intended for app use
* [immutant.wildfly](immutant.wildfly.html) - in-container specific functions

## [immutant.web](immutant.web.html)

* `start` is now `run`
* `start-servlet` is also now `run`
* `current-servlet-request` currently has no analogue

### immutant.web.session -> obviated by wrap-session
### immutant.web.servlet -> REMOVED
### immutant.web.session.internal -> REMOVED

### [immutant.web.middleware](immutant.web.middleware.html)

Contains only `wrap-development` and `wrap-session`

## immutant.xa -> immutant.transactions

Listeners are no longer automatically enlisted participants in an XA
transaction. Within the handler fn, you must now explicitly define a
transaction using one of the macros in `immutant.transactions`. If an
exception escapes that body, the tx will be rolled back, and if the
exception bubbles out of the handler, the message will be queued for
redelivery. But the rollback of the tx has no relationship to
redelivery, which is only triggered by the exception.
