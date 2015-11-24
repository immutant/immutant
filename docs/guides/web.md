---
{:title "Web"
 :sequence 1.5
 :base-ns immutant.web
 :description "Running Clojure web applications"}
---

The `org.immutant/web` library changed quite a bit from Immutant 1.x
to 2.x, both its API and its foundation: the [Undertow] web server.
Among other things, this resulted in
[much better performance](https://github.com/ptaoussanis/clojure-web-server-benchmarks)
(~35% more throughput than v1.1.1) and built-in support for
WebSockets.

## The Namespaces

The primary namespace, [[immutant.web]], includes the two main functions
you'll use to run your handlers:

* `run` - runs your handler in a specific environment, responding to
  web requests matching a given host, port, path and virtual host. The
  handler may be either a [Ring] function, Servlet instance, or
  Undertow HttpHandler
* `stop` - stops your handler[s]

Also included:

* `run-dmc` - runs your handler in *Development Mode* (the 'C' is silent)
* `server` - provides finer-grained control over the embedded web
  server hosting your handler[s].

The [[immutant.web.middleware]] namespace provides some Ring
middleware:

* `wrap-websocket` - attach websocket callbacks to your Ring handler
* `wrap-session` - enables session sharing among your Ring handler and
  its WebSockets, as well as automatic session replication when your
  app is deployed to a WildFly cluster.
* `wrap-development` - included automatically by `run-dmc`, this
  aggregates some middleware handy during development.

The [[immutant.web.async]] namespace enables the creation of
[WebSockets] and [HTTP streams]. And support for [Server-Sent Events]
is provided by [[immutant.web.sse]].

The [[immutant.web.undertow]] namespace exposes tuning options for
Undertow, the ability to open additional listeners, and flexible SSL
configuration.

## A sample REPL session

Now, let's fire up a REPL and work through some of the features of the
library.

If you haven't already, you should read through the [installation]
guide and require the `immutant.web` namespace at a REPL to follow
along:

```clojure
(require '[immutant.web :refer :all])
```

### Common Usage

First, you'll need a [Ring] handler. If you generated your app using a
template from [Compojure], [Luminus], [Caribou] or some other
Ring-based library, yours will be associated with the `:handler` key
of your `:ring` map in your `project.clj` file. Of course, a far less
fancy handler will suffice:

```clojure
(defn app [request]
  {:status 200
   :body "Hello world!"})
```

To make the app available at <http://localhost:8080/>, do this:

```clojure
(run app)
```

Which, if we make the default values explicit, is equivalent to this:

```clojure
(run app {:host "localhost" :port 8080 :path "/"})
```

Or, since [[run]] takes options as either an explicit map or keyword
arguments (kwargs), this:

```clojure
(run app :host "localhost" :port 8080 :path "/")
```

The options passed to `run` determine the URL used to invoke your
handler: `http://{host}:{port}{path}`

To replace your `app` handler with another, just call run again with
the same options, and it'll replace the old handler with the new:

```clojure
(run (fn [_] {:status 200 :body "hi!"}))
```

To stop the handler, do this:

```clojure
(stop)
```

Which is equivalent to this:

```clojure
(stop {:host "localhost" :port 8080 :path "/"})
```

Or like `run`, if you prefer kwargs, this:

```clojure
(stop :host "localhost" :port 8080 :path "/")
```

Alternatively, you can save the return value from `run` and pass it to
stop to stop your handler.

```clojure
(def server (run app {:port 4242 :path "/hello"}))
...
(stop server)
```

Stopping your handlers isn't strictly necessary if you're content to
just let the JVM exit, but it can be handy at a REPL.

### Advanced Usage

The `run` function returns a map that includes the options passed to
it, so you can thread `run` calls together, useful when your
application runs multiple handlers. For example,

```clojure
(def everything (-> (run hello)
                  (assoc :path "/howdy")
                  (->> (run howdy))
                  (merge {:path "/" :port 8081})
                  (->> (run hola))))
```

The above actually creates two Undertow web server instances: one
serving requests for the `hello` and `howdy` handlers on port 8080,
and one serving `hola` responses on port 8081.

You can stop all three apps (and shutdown the two web servers) like
so:

```clojure
(stop everything)
```

Alternatively, you could stop only the `hola` app like so:

```clojure
(stop {:path "/" :port 8081})
```

You could even omit `:path` since "/" is the default. And because
`hola` was the only app running on the web server listening on port
8081, it will be shutdown automatically.

## Virtual Hosts

The `:host` option denotes the IP interface to which the web server is
bound, which may not be publicly accessible. You can extend access to
other hosts using the `:virtual-host` option, which takes either a
single hostname or multiple:

```clojure
(run app :virtual-host "yourapp.com")
(run app :virtual-host ["app.io" "app.us"])
```

Multiple applications can run on the same `:host` and `:port` as long
as each has a unique combination of `:virtual-host` and `:path`.

## Advanced Undertow Configuration

The [[immutant.web.undertow]] namespace includes a number of
composable functions that turn a map of various keywords into a map
containing an `io.undertow.Undertow$Builder` instance mapped to the
keyword, `:configuration`. So Undertow configuration is exposed via a
composite of these functions called [[immutant.web.undertow/options]].

For a contrived example, say we wanted our handler to run with 42
worker threads, and listen for requests on two ports, 8888 and 9999.
Weird, but possible. To do it, we'll need to pass the `:port` option
twice, in a manner of speaking:

```clojure
(require '[immutant.web.undertow :refer (options)])
(def opts (-> (options :port 8888 :worker-threads 42)
            (assoc :port 9999)
            options))
(run app opts)
```

### TLS/SSL

SSL and Java is a notoriously gnarly combination that is way outside
the scope of this guide. Ultimately, Undertow needs either a
`javax.net.ssl.SSLContext` or a `javax.net.ssl.KeyManager[]` and an
optional `javax.net.ssl.TrustManager[]`.

You may also pass a `KeyStore` instance or a path to one on disk, and
the `SSLContext` will be created for you. For example,

```clojure
(run app (immutant.web.undertow/options
           :ssl-port 8443
           :keystore "/path/to/keystore.jks"
           :key-password "password"))
```

Another option is to use the [less-awful-ssl] library; maybe something
along these lines:

```clojure
(def context (less.awful.ssl/ssl-context "client.pkcs8" "client.crt" "ca.crt"))
(run app (immutant.web.undertow/options
           :ssl-port 8443
           :ssl-context context))
```

Client authentication may be specified using the `:client-auth`
option, where possible values are `:want` and `:need`. Or, if you're
fancy, `:requested` and `:required`.

### HTTP/2

There are three steps to enabling HTTP/2 or SPDY:

* Set the `:http2?` option to `true`
* Configure an SSL listener (see previous section)
* Prepend the appropriate `alpn-boot.jar` to your *bootclasspath*

You'll need to consult the [ALPN] docs to know which version of
`alpn-boot.jar` is appropriate for your JVM version. Most importantly,
it needs to be in the *bootclasspath*, e.g.

    java -Xbootclasspath/p:{/path/to/alpn-boot.jar} ...

See the [Immutant Feature Demo] for an HTTP/2 configuration example.

## Handler Types

Though the handlers you run will typically be Ring functions, you can
also pass any valid implementation of `javax.servlet.Servlet` or
`io.undertow.server.HttpHandler`. For an example of the former, here's
a very simple [Pedestal] service running on Immutant:

```clojure
(ns testing.hello.service
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route.definition :refer [defroutes]]
            [ring.util.response :refer [response]]
            [immutant.web :refer [run]]))

(defn home-page [request] (response "Hello World!"))
(defroutes routes [[["/" {:get home-page}]]])
(def service {::http/routes routes})

(defn start [options]
  (run (::http/servlet (http/create-servlet service)) options))
```

## Development Mode

The [[run-dmc]] macro resulted from a desire to provide a no-fuss way to
enjoy all the benefits of REPL-based development. Before calling
`run`, `run-dmc` will first ensure that your Ring handler is
var-quoted and wrapped in the `reload` and `stacktrace` middleware
from the [ring-devel] library (which must be included among your
`[:profiles :dev :dependencies]` in `project.clj`). It'll then open
your app in a browser.

Both `run` and `run-dmc` accept the same options. You can even mix
them within a single threaded call.

## Asynchrony

[WebSockets], [HTTP streams], and [Server-Sent Events] are all enabled
by the [[immutant.web.async/as-channel]] function, which should be
called from your Ring handler, as it takes a request map and some
callbacks and returns a valid response map. Its polymorphic design
enables graceful degradation from bidirectional WebSockets to
unidirectional chunked responses, e.g. streams. In either case, data
is sent from the server using [[immutant.web.async/send!]].

It's important to note that `as-channel` returns a normal Ring
response map, so it's completely compatible with Ring middleware that
might affect other entries in the response, allowing you to `assoc`
`:status`, `:headers`, etc on to it. The only requirement is that the
`:body` entry needs to be ultimately returned by any downstream
middleware.

The signatures of the callback functions supported by `as-channel` are
as follows:

```clojure
  :on-open    (fn [channel])
  :on-close   (fn [channel {:keys [code reason]}])
  :on-error   (fn [channel throwable])
  :on-message (fn [channel message])
```

The `:on-message` handler is only relevant to WebSockets, as are the
`:code` and `:reason` keys passed to `:on-close`: they will be nil for
HTTP streams.

### HTTP Streams

Creating chunked responses is straightforward, as the following Ring
handler demonstrates:

```clojure
(require '[immutant.web.async :as async])

(defn app [request]
  (async/as-channel request
    {:on-open (fn [stream]
                (dotimes [msg 10]
                  (async/send! stream (str msg) {:close? (= msg 9)})
                  (Thread/sleep 1000))})))
(run app)
```

When a client connects to our app, the `:on-open` handler is
asynchronously called with the appropriate channel. Our contrived
callback sends a number to the client every second. On the 10th time
it sets the `:close?` option to true. Its default value is false,
causing the channel to remain open after the data is sent.

If you don't know the status or headers that you need to send until
the `send!` call, you can pass a map of the form `{:body msg :status
code :headers [...]}` in place of the message, but only on the *first*
send to that channel. A `:status` or `:headers` value in that map will
override the `:status` or `:headers` returned by the Ring handler
invocation that called `as-channel`.

The message passed to `send!` (or the `:body` of a map passed to
`send!`) can be any of the standard Ring body types (`String`, `File`,
`InputStream`, `ISeq`), as well as `byte[]`.

### WebSockets

To support graceful client degradation, WebSockets are coded exactly
like HTTP Streams, except that an additional callback option is
supported, `:on-message`, for bidirectional communication.

```clojure
(def callbacks
  {:on-message (fn [ch msg]
                 (async/send! ch (.toUpperCase msg)))})

(defn app [request]
  (async/as-channel request callbacks))

(run app)
```

The message passed to `send!` can be any of the standard Ring body
types (`String`, `File`, `InputStream`, `ISeq`), as well as
`byte[]`. Note that each entry in an `ISeq` will pass through `send!`,
so will be sent as at least one message (more if the entry itself is a
type that triggers multiple messages). `File`s and `InputStream`s may
also be broken up in to multiple messages if they are too large (we
hint that they should be sent as up to 16KB messages, but the actual
sizes of the messages may vary, depending on the WildFly or Undertow
heuristics and configuration).

You can identify a WebSocket upgrade request by the presence of
`:websocket?` in the request map. This enables you to construct your
handlers so that they correctly respond to both normal HTTP requests
as well as WebSockets.

```clojure
(defn app [request]
  (if (:websocket? request)
    (async/as-channel request callbacks)
    (-> request
      (get-in [:params "msg"])
      .toUpperCase
      ring.util.response/response)))

(run app)
```

Immutant provides a convenient Ring middleware function that
encapsulates the check for the upgrade request:
[[immutant.web.middleware/wrap-websocket]].

```clojure
(web/run (-> my-app
           (wrap-websocket callbacks)))
```

But using `wrap-websocket` means losing the `request` closure in your
Ring handler, representing the original WebSocket upgrade request from
the client. You can still access it, however, with
[[immutant.web.async/originating-request]].

Note the `:path` argument to [[immutant.web/run]] applies to both the
Ring handler and the WebSocket, distinguished only by the request
protocol. Given a `:path` of "/foo", for example, you'd have both
`http://your.host.com/foo` and `ws://your.host.com/foo`.

### Server-Sent Events (SSE)

[Server-Sent Events] are a stream of specially-formatted chunked
responses with a `Content-Type` header of `text/event-stream`. The
[[immutant.web.sse]] namespace provides its own `send!` and
`as-channel` functions that are composed from their
[[immutant.web.async]] counterparts. *Events* are polymorphic: any
`Object` other than a `Collection` or `Map` is considered a simple
data field that will be string-ified, prefixed with "data:", and
suffixed with "\n". A `Collection` represents a multi-line data field.
And a `Map` is expected to contain at least one of the following keys:
`:event`, `:data`, `:id`, and `:retry`.

Let's modify the HTTP streaming example to use SSE:

```clojure
(require '[immutant.web.sse :as sse])

(defn app [request]
  (sse/as-channel request
    {:on-open (fn [stream]
                (dotimes [e 10]
                  (sse/send! stream e)
                  (Thread/sleep 1000))
                (sse/send! stream {:event "close", :data "bye!"}))}))

(run app)
```

Because we're using `sse/send!` the client will receive
newline-delimited messages formatted with field names, e.g.

```
data: 0

data: 1

 ...

data: 8

data: 9

event: close
data: bye!

```

And note that most EventSource clients will attempt to reconnect if
the server closes the connection, so instead we send a special "close"
event on which our client can dispatch to initiate the close.

### Knowing when a send! completes or fails

Calling `send!` (`sse/` or `async/`) is an async operation - the send
is immediately queued, and `send!` returns to the caller. To know when
the send has completed, you can provide an `:on-success` callback. You
can also provide an `:on-error` callback to know when an error occurs:

```
(async/send! ch a-message
  :on-success #(println "yay!")
  :on-error   (fn [e] (println "boo!" e)))
```

## Feature Demo

We maintain a Leiningen project called the [Immutant Feature Demo]
demonstrating all the Immutant namespaces, including simple examples
of
[the features described herein](https://github.com/immutant/feature-demo/blob/master/src/demo/web.clj).

You should be able to clone it somewhere, cd there, and `lein run`.

Have fun!

[Undertow]: http://undertow.io/
[Ring]: https://github.com/ring-clojure/ring/wiki
[installation]: guide-installation.html
[Pedestal]: https://github.com/pedestal/pedestal
[Compojure]: https://github.com/weavejester/compojure
[Luminus]: http://www.luminusweb.net/
[Caribou]: http://let-caribou.in/
[ring-devel]: https://github.com/ring-clojure/ring/tree/master/ring-devel
[WebSockets]: http://en.wikipedia.org/wiki/WebSocket
[Immutant Feature Demo]: https://github.com/immutant/feature-demo
[less-awful-ssl]: https://github.com/aphyr/less-awful-ssl
[Server-Sent Events]: http://www.w3.org/TR/eventsource/
[HTTP streams]: http://en.wikipedia.org/wiki/Chunked_transfer_encoding
[ALPN]: http://www.eclipse.org/jetty/documentation/current/alpn-chapter.html
