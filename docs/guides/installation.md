---
{:title "Installation"
 :sequence 0.5
 :description "Using the Immutant libraries in your application"}
---

Installation of Immutant 1.x was atypical of most Clojure libraries,
because that distribution included a fork of the JBoss AS7 application
server. In Immutant 2.x, the application server is gone, so there is
no explicit installation step.

## project.clj

To use Immutant, you need two things in your `project.clj`:

* Immutant lib[s] in your `:dependencies`
* a `:main` function or namespace

### :dependencies

Here's an example making all the Immutant libraries available in a
project's classpath:

```clojure
(defproject some-project "1.2.3"
  ...
  :dependencies [[org.immutant/web "{{version}}"]
                 [org.immutant/caching "{{version}}"]
                 [org.immutant/messaging "{{version}}"]
                 [org.immutant/scheduling "{{version}}"]
                 [org.immutant/transactions "{{version}}"]])
```

You would of course only include the libs you need, but if you really
did use all of them, or you just want to experiment, we provide an
aggregate that brings them all in transitively:

```clojure
(defproject some-project "1.2.3"
  ...
  :dependencies [[org.immutant/immutant "{{version}}"]]
```

### :main

With the dependencies in place, you simply invoke the Immutant
services from your app's main entry point, identified by the `:main`
key in your `project.clj`.

If you created your project with the `app` template, like so:

    lein new app my-app

Then the value of the `:main` entry in `project.clj` will be
`my-app.core` and the `-main` function in `src/my_app/core.clj`
is where you should invoke the Immutant services. For example:

```clojure
(ns my-app.core
  (:require [immutant.web :as web])
  (:gen-class))

(defn app [request]
  {:status 200
   :body "Hello world!"})

(defn -main []
  (web/run app))
```

You can also specify a fully-qualified symbol for `:main` that points
to function to use instead of `-main`:

```clojure
:main my-app.core/start
```

But what if your project doesn't have a `:main` function? If you
created your app with a popular Ring-based template like [Compojure]
or [Luminus], it won't:

    lein new compojure my-app

Instead, you'll have a `:ring` map with a `:handler` called
`my-app.handler/app`. So you'll need to manually add a `:main` entry
referencing a function in your project. You can easily add one to
`src/my_app/handler.clj`:

```clojure
(ns my-app.handler
  ...
  (:require [immutant.web :as web])
  ... )

(def app ... )

(defn start []
  (web/run app))
```

With `:main` set to `my-app.handler/start` in `project.clj`, you can
then start your app like so:

    lein run

## Incremental Builds

If you need cutting-edge features/fixes that aren't in the latest
release, you can use an incremental build.

Our CI server publishes an [incremental release][builds] for each
successful build. In order to use an incremental build, you'll need to
add a repository to your `project.clj`:

```clojure
(defproject some-project "1.2.3"
  ...
  :dependencies [[org.immutant/immutant "2.x.incremental.BUILD_NUMBER"]]
  :repositories [["Immutant 2.x incremental builds"
                  "http://downloads.immutant.org/incremental/"]])
```

You should replace **BUILD_NUMBER** with the actual build number
for the version you want to use. You can obtain this from our [builds]
page.


## Additional Resources

The API docs for the latest Immutant release are always available here:

* [http://immutant.org/documentation/current/apidoc/](/documentation/current/apidoc/)

as well as the API docs for the [latest CI build][latest-api].

If you are interested in using Immutant inside a [WildFly] container,
see our [WildFly guide].

[builds]: http://immutant.org/builds/2x/
[latest-api]: https://projectodd.ci.cloudbees.com/job/immutant2-incremental/lastSuccessfulBuild/artifact/target/apidocs/index.html
[The Deuce]: http://immutant.org/news/2014/04/02/the-deuce/
[Compojure]: https://github.com/weavejester/compojure
[Luminus]: http://www.luminusweb.net/
[WildFly]: http://wildfly.org/
[WildFly guide]: guide-wildfly.html
