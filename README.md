# Immutant [![Build Status](https://travis-ci.org/immutant/immutant.svg?branch=thedeuce)](https://travis-ci.org/immutant/immutant)

Immutant is an integrated suite of libraries providing commodity
services for Clojure applications. This README covers building
Immutant. For usage and more details, see http://immutant.org/.

To file an issue, see https://issues.jboss.org/browse/IMMUTANT


## Requirements

* Leiningen 2.3.4+


## Building

From the root of the project, run:

    lein modules all

The `modules` higher-order task will run the task passed to it (e.g.
the `all` alias) in all that project's child modules. If you cd inside
one of the child modules, you can just run lein as you normally would,
but you should run the above at least once so that interdependent
modules are installed in your local repo.


## License

Immutant is licensed under the Apache License, v2. See
[LICENSE](LICENSE) for details.


