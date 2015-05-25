# Immutant [![Build Status](https://travis-ci.org/immutant/immutant.svg?branch=thedeuce)](https://travis-ci.org/immutant/immutant)

Immutant is an integrated suite of libraries providing commodity
services for Clojure applications. This README covers building
Immutant. For usage and more details, see http://immutant.org/.

To file an issue, see https://issues.jboss.org/browse/IMMUTANT

## Latest Release

Leiningen:

``` clj
[org.immutant/immutant "2.0.1"]
```

Maven:

``` xml
<dependency>
  <groupId>org.immutant</groupId>
  <artifactId>immutant</artifactId>
  <version>2.0.1</version>
</dependency>
```

## Building

Requires Leiningen 2.3.4+

From the root of the project, run:

    lein modules all

The `modules` higher-order task will run the task passed to it (e.g.
the `all` alias) in all that project's child modules. If you cd inside
one of the child modules, you can just run lein as you normally would,
but you should run the above at least once so that interdependent
modules are installed in your local repo.

To run the integration tests as part of a full build, build with:

    lein with-profile +integs modules all

For information on setting up WildFly for the integration tests, or on
running them individually, see
[the README in integration-tests/](integration-tests/README.md).

## License

Immutant is licensed under the Apache License, v2. See
[LICENSE](LICENSE) for details.
