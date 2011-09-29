# basic-ring

A throwaway app that can be deployed to (fn box) in its current state.

## Usage

1. Install lein: https://github.com/technomancy/leiningen
2. Run `lein deps` from the project root
3. Edit <root>/basic-ring.clj, and change the :root path to point to the actual location of the project
4. Use the admin-console to deploy <root>/basic-ring.clj

Yes, this is hacky as hell at the moment, and we have to put too much in
the clj descriptor currently.

