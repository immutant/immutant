---
{:title "Caching"
 :sequence 4
 :base-ns 'immutant.caching
 :description "Flexible caching and memoization using a linearly-scalable data grid"}
---

Immutant caching is provided by the [Infinispan] data grid, the
distributed features of which are available when deployed to a WildFly
or EAP cluster. But even in "local mode", i.e. not in a cluster but
locally embedded within your app, Infinispan caches offer features
such as eviction, expiration, persistence, and transactions that
aren't available in typical [ConcurrentMap] implementations.

This guide will explore the [[immutant.caching]] namespace, which
provides access to Infinispan, whether your app is deployed to a
WildFly/EAP cluster or not. The API has changed quite a bit in "The
Deuce" from 1.x, which we'll point out as we go along.

## Creation and Configuration

Caches are created, started, and referenced using the
[[immutant.caching/cache]] function. It accepts a number of optional
configuration arguments, but the only required one is a name, since
every cache must be uniquely named. If you pass the name of a cache
that already exists, a reference to the existing cache will be
returned, effectively ignoring any additional config options you might
pass. So two Immutant cache instances with the same name will be
backed by the same Infinispan cache.

If you wish to reconfigure an existing cache, you must stop it first
by calling [[immutant.caching/stop]]. This is a significant change from
1.x, which included `create`, `lookup`, and `lookup-or-create`
functions, but no `stop`. In 2.x, those have been replaced by `cache` and
`stop`.

Infinispan is a veritable morass of enterprisey configuration.
Immutant tries to strike a convention/configuration balance by
representing the more common options as keywords passed to the `cache`
function, while still supporting the more esoteric config via
[[immutant.caching/builder]] and Java interop.

See the [[immutant.caching/cache]] apidoc for a list of its supported
options, passed as either an explicit map or "kwargs" (keyword
arguments).

## Example Usage

The following examples are taken from the Immutant
[Feature Demo][feature-demo] which you can clone and run locally at a
REPL if you're a "follow along" type.

Caches are inherently mutable. In 1.x, we provided a `Mutable`
protocol, the functions of which merely invoked the corresponding
[ConcurrentMap] methods implemented by the Infinispan caches. In *The
Deuce*, `Mutable` has been removed, as we felt it offered little value
over the simple Java interop Clojure provides anyway.

### Reading

Because they implement `java.util.Map`, Clojure's core functions are
all you need to read data from an Immutant cache.

```clojure

  (def bar (immutant.caching/cache "bar"))
  (.putAll bar {:a 1, :b {:c 3, :d 4}})

  ;; Use get to obtain associated values
  (get bar :a)                            ;=> 1
  (get bar :x)                            ;=> nil
  (get bar :x 42)                         ;=> 42

  ;; Symbols look up their value
  (:a bar)                                ;=> 1
  (:x bar 42)                             ;=> 42

  ;; Nested structures work as you would expect
  (get-in bar [:b :c])                    ;=> 3

  ;; Use find to return entries
  (find bar :a)                           ;=> [:a 1]

  ;; Use contains? to check membership
  (contains? bar :a)                      ;=> true
  (contains? bar :x)                      ;=> false

```

### Writing

In addition to Java interop, [[immutant.caching/swap-in!]] may
be used to cache entries atomically, providing a consistent view of
the cache to callers. Internally, it uses the [ConcurrentMap] methods,
`replace` to swap values with existing entries, and `putIfAbsent` when
the entry doesn't exist.

```clojure

  (def foo (cache "foo"))

  (swap-in! foo :a (fnil inc 0))         ;=> 1
  (swap-in! foo :b (constantly "foo"))   ;=> "foo"
  (swap-in! foo :a inc)                  ;=> 2

```

Of course, plain ol' interop works, too:

```clojure

  ;; Put an entry in the cache
  (.put foo :a 1)

  ;; Add all the entries in the map to the cache
  (.putAll foo {:b 2, :c 3})

  ;; Put it in only if key is not already present
  (.putIfAbsent foo :b 6)               ;=> 2
  (.putIfAbsent foo :d 4)               ;=> nil

  ;; Put it in only if key is already present
  (.replace foo :e 5)                   ;=> nil
  (.replace foo :b 6)                   ;=> 2

  ;; Replace for specific key and value (compare-and-set)
  (.replace foo :b 2 0)                 ;=> false
  (.replace foo :b 6 0)                 ;=> true

```

### Removing

Cache entries can be explicitly deleted using Java interop, but they
can also be subject to automatic expiration and eviction.

```clojure

  ;; Removing a missing key is harmless
  (.remove baz :missing)                  ;=> nil

  ;; Removing an existing key returns its value
  (.remove baz :b)                        ;=> 2

  ;; If value is passed, both must match for remove to succeed
  (.remove baz :c 2)                      ;=> false
  (.remove baz :c 3)                      ;=> true

  ;; Clear all entries
  (.clear baz)

```

#### Expiration

By default, cached entries never expire, but you can trigger
expiration by passing the `:ttl` (time-to-live) and/or `:idle` options
to the `cache` function. Their units are milliseconds, but can also be
represented as a keyword or a vector of multiplier/keyword pairs, e.g.
`[1 :week, 4 :days, 2 :hours, 30 :minutes, 59 :seconds]`. Both
singular and plural keywords are valid.

If `:ttl` is specified, entries will be automatically deleted after
that amount of time elapses, starting from when the entry was added.
Effectively, this is the entry's "maximum lifespan". If `:idle` is
specified, the entry is deleted after the time elapses, but the
"timer" is reset each time the entry is accessed. If both are
specified, whichever elapses first "wins" and triggers expiration.

It's possible to vary the `:ttl` and `:idle` times among entries in a
single cache using the [[with-expiration]] function:

```clojure

  (def baz (cache "baz", :ttl [5 :minutes], :idle [1 :minute]))
  (.putAll baz {:a 1 :b 2 :c 3})
  (let [c (with-expiration baz :ttl [1 :hour] :idle [20 :minutes])]
    (swap-in! c :a dec)

```

#### Eviction

To avoid memory exhaustion, you can include the `:max-entries` option
to [[immutant.caching/cache]] as well as the `:eviction` policy to
determine which entries to evict. And if the `:persist` option is set,
evicted entries are not deleted but rather flushed to disk so that the
entries in memory are always a finite subset of those on disk.

The default eviction policy is [:lirs], which is an optimized version
of `:lru` (Least Recently Used).

```clojure

  (def baz (cache "baz", :max-entries 3))
  (.putAll baz {:a 1 :b 2 :c 3})
  (:a baz)                              ;=> 1
  (select-keys baz [:b :c])             ;=> {:c 3, :b 2}
  (.put baz :d 4)
  (:a baz)                              ;=> nil

```

### Encoding

Cache entries are not encoded by default, but may be decorated with a
codec using the [[with-codec]] function. Provided codecs include `:edn`,
`:json`, and `:fressian`, but the latter two require additional
dependencies: `cheshire` and `org.clojure/data.fressian`,
respectively.

Encoding entries is typically necessary only when non-clojure clients
are sharing your cache. And if you wish to store nil keys or values, a
codec is required.

```clojure

  (def baz (cache "baz"))
  (def encoded (with-codec baz :edn))

  (.put encoded :a {:b 42})
  (:a encoded)                          ;=> {:b 42}

  ;; Access via non-encoded caches still possible
  (get baz :a)                          ;=> nil
  (get baz ":a")                        ;=> "{:b 42}"

```

### Memoizing

In Immutant 1.x, the caching namespace included a `memo` function that
enabled [memoization] backed by an Infinispan cache. This forced a
transitive dependency on specific versions of [core.memoize] and
[core.cache] that occasionally conflicted with other libraries.

In *The Deuce*, we moved `memo` to its own namespace,
[[immutant.caching.core-memoize]], along with a corresponding
[[immutant.caching.core-cache]]. So if you wish to call `memo`, your app
must declare a dependency on [core.memoize].

Here's a contrived example showing how memoization incurs the expense
of calling a slow function only once:

```clojure
  (defn slow-fn [& _]
    (Thread/sleep 5000)
    42)

  ;; Other than the function to be memoized, arguments are the same as
  ;; for the cache function.
  (def memoized-fn (memo slow-fn "memo", :ttl [5 :minutes]))

  ;; Invoking the memoized function fills the cache with the result
  ;; from the slow function the first time it is called.
  (memoized-fn 1 2 3)                     ;=> 42

  ;; Subsequent invocations with the same arguments return the result
  ;; from the cache, avoiding the overhead of the slow function
```

## Clustering

Each Infinispan cache operates in one of four modes. Normally, *local*
mode is your only option, but when your app is deployed to a cluster,
you get three more: *invalidated*, *replicated*, and *distributed*.
These modes define how peers collaborate to replicate your data
throughout the cluster. Further, you can choose whether this
collaboration occurs asynchronous to the write.

In Immutant 1.x, there were two options, `:mode` and `:sync`, so to
configure asynchronous distributed mode, for example, you would set
`:mode :distributed, :sync false`. In *The Deuce*, we've eliminated the
`:sync` option, so instead you'd set `:mode :dist-async`.

* `:local` This is the only supported mode outside of a cluster
* `:dist-sync` `:dist-async` This mode enables Infinispan caches to
  achieve "linear scalability". Cache entries are copied to a fixed
  number of peers (2, by default) regardless of the cluster size.
  Distribution uses a consistent hashing algorithm to determine which
  nodes will store a given entry.
* `:invalidation-sync` `:invalidation-async` No data is actually
  shared among the cluster peers in this mode. Instead, notifications
  are sent to all nodes when data changes, causing them to evict their
  stale copies of the updated entry.
* `:repl-sync` `:repl-async` In this mode, entries added to any peer
  will be copied to all other peers in the cluster, and can then be
  retrieved locally from any instance. This mode is probably
  impractical for clusters of any significant size. Infinispan
  recommends 10 as a reasonable upper bound on the number of
  replicated nodes.

The simplest way to take advantage of Infinispan's clustering
capabilities is to deploy your app to a [WildFly] cluster.

[ConcurrentMap]: http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ConcurrentMap.html
[Infinispan]: http://infinispan.org
[feature-demo]: https://github.com/immutant/feature-demo/blob/thedeuce/src/demo/caching.clj
[:lirs]: http://en.wikipedia.org/wiki/LIRS_caching_algorithm
[core.cache]: https://github.com/clojure/core.cache
[core.memoize]: https://github.com/clojure/core.memoize
[memoization]: http://en.wikipedia.org/wiki/Memoization
[WildFly]: guide-wildfly.html
