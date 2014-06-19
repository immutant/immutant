# Immutant 1.1 -> 2.0 migration guide

This currently tracks changes between the 1.1 and 2.0 API, and will
eventually be a more thorough migration guide.

Structure: each section covers a namespace. If the namespace has been
renamed, that will be reflected by old namespace -> new namespace. If
the namespace has yet to be ported (but probably will, it's marked
with "-> ?". If it is now gone, it's marked with "REMOVED".

This list includes all of the Immutant namespaces, some of which
were/are for internal use only.


## immutant.cache -> immutant.caching

The ns exists, but is currently empty.

### immutant.cache.config -> ?
### immutant.cache.core -> ?
### immutant.cache.wrapper -> ?

## immutant.codecs

:text codec was removed. only supplied codecs in 2.x:
:none, :clojure, :edn, :json, and :fressian

## immutant.daemons -> ?

Will likely be ported, but has little value outside of the container.

## immutant.dev REMOVED

Used for dev inside the container, but you can get all these same
facilities with standard tools outside of the container with 2.x, and
we're no longer exposing the project map, so this wouldn't be very
useful inside the container with 2.x.

## immutant.jobs -> immutant.scheduling

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

## immutant.logging

Do we still need this?

## immutant.messaging

Has a similar API, except around destination creation and passing. Fns
now take destination objects instead of strings, and the destination
objects must be created via `create-queue` and `create-topic`.

`unlisten` and `stop` have been merged in to `stop`. `message-seq` is
no more.

### immutant.messaging.codecs

Brought over with few changes.

### immutant.messaging.core REMOVED

Merged with `immutant.messaging.internal`.

### immutant.messaging.hornetq -> ?

Will likely be brought over.

### immutant.messaging.internal

Brought over, but with a drastically different API.

## immutant.pipeline -> ?

Will likely be brought over.

## immutant.registry REMOVED

## immutant.repl

Still there, but with a different API. It's now only used inside the
container, and is incomplete ATM - it likely needs to expose
middleware support.

## immutant.resource-util REMOVED

## immutant.runtime REMOVED

### immutant.runtime.bootstrap REMOVED

## immutant.runtime-util REMOVED

## immutant.util

Split across three namespaces:

* immutant.util - fns appropriate for app use
* immutant.internal.util - fns used by Immutant itself, and not intended for app use
* immutant.wildfly - in-container specific functions

## immutant.web

* `start` is now `run` or `mount`, the latter with a different signature
* `stop` is now `unmount`
* `start-servlet` is now `mount-servlet`, but with a different signature
* `current-servlet-request` currently has no analogue

### immutant.web.session -> ?
### immutant.web.servlet -> ?
### immutant.web.session.internal -> ?

### immutant.web.middleware

Confirm what middleware is there. Was any of this ever used directly
by users?

### immutant.web.internal -> ?

## immutant.xa -> ?

### immutant.xa.wrappers -> ?

### immutant.xa.transaction -> ?
