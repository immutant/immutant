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

## immutant.codecs -> ?

## immutant.daemons -> ?

Will likely be ported, but has little value outside of the container.

## immutant.dev REMOVED

Used for dev inside the container, but you can get all these same
facilities with standard tools outside of the container with 2.x, and
we're no longer exposing the project map, so this wouldn't be very
useful inside the container with 2.x.

## immutant.jobs -> immutant.scheduling

TODO: talk about API changes

### immutant.jobs.internal REMOVED

## immutant.logging

Do we still need this?

## immutant.messaging

The ns exists, but is currently empty.

### immutant.messaging.codecs -> ?

### immutant.messaging.core -> ?

Will likely be merged with `internal`?

### immutant.messaging.hornetq -> ?

### immutant.messaging.internal -> ?

## immutant.pipeline -> ?

## immutant.registry

Do we still need this?

## immutant.repl

Still there, but with a different API. It's now only used inside the
container, and is incomplete ATM - it likely needs to expose
middleware support.

## immutant.resource-util REMOVED

## immutant.runtime REMOVED

### immutant.runtime.bootstrap REMOVED

## immutant.runtime-util REMOVED

## immutant.util

Currently still fairly similar to 1.x, but needs cleanup.

## immutant.web

TODO: doc API changes.

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








 
