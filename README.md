# rest-exercise

Simple Clojure implementation of
[this exercise](https://docs.google.com/document/d/1ZWcTzQdQ9zSZ8Tv4XDyCrju40_FuSJ7W6qu4-pUU-ZA/pub).

The service uses [compojure](https://github.com/weavejester/compojure)
to route requests to appropriate handlers.
[ring-json](https://github.com/ring-clojure/ring-json) handles JSON (de)serialization.


## Usage

### Running with lein ring

1. Ensure you have [leiningen](https://leiningen.org/) 2.7.1 or better.
1. Clone this repository.
1. Run `lein ring server-headless` from any directory within the project.
1. The service should be running on http://localhost:3000.

It is possible to specify the port on which the service will run by
changing the `PORT` environment variable, e.g.,

`PORT=5555 lein ring server-headless`

will start the service on port 5555. See
[lein-ring on github](https://github.com/weavejester/lein-ring)
for more options.


### Building and Running Uberjars

`lein ring uberjar` will build a standalone executable jar file in the
`target` directory. From that directory, run

    $ java -jar rest-exercise-0.1.0-standalone.jar

to start the service. The `PORT` environment variable is effective
when running the standalone service as well.

## License

Copyright © 2017 Mike Conley

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
