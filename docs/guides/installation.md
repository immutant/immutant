---
{:title "Installation"
 :sequence 0.5
 :description "Using the Immutant libraries in your application"}
---

Installation of Immutant 1.x was atypical of most Clojure libraries,
because that distribution included a forked JBoss AS7 app server. In
Immutant 2.x, the app server is gone, so there is no installation step
for [The Deuce].

You simply declare the libraries as dependencies in your project, the
same way you would any other Clojure library. For example:

```clojure
(defproject some-project "1.2.3"
  ...
  :dependencies [[org.immutant/web "{{version}}"]
                 [org.immutant/caching "{{version}}"]
                 [org.immutant/messaging "{{version}}"]
                 [org.immutant/scheduling "{{version}}"]])
```

We're bringing in the artifacts piecemeal above, but we also provide
an aggregate that brings them all in transitively:

```clojure
(defproject some-project "1.2.3"
  ...
  :dependencies [[org.immutant/immutant "{{version}}"]]
```

The API docs for the latest release are always available at:

* [http://immutant.org/documentation/current/apidoc/](/documentation/current/apidoc/)

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

Along with the artifacts, each CI build publishes
[the API docs][latest-api] for all of the Immutant 2.x namespaces.


[builds]: http://immutant.org/builds/2x/
[latest-api]: https://projectodd.ci.cloudbees.com/job/immutant2-incremental/lastSuccessfulBuild/artifact/target/apidocs/index.html
[The Deuce]: http://immutant.org/news/2014/04/02/the-deuce/
