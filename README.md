# rest-exercise

Simple Clojure implementation of
[this exercise](https://docs.google.com/document/d/1ZWcTzQdQ9zSZ8Tv4XDyCrju40_FuSJ7W6qu4-pUU-ZA/pub).

The service uses [compojure](https://github.com/weavejester/compojure)
to route requests to appropriate handlers.
[ring-json](https://github.com/ring-clojure/ring-json) handles JSON (de)serialization.

[libphonenumber](https://github.com/googlei18n/libphonenumber/) is
used to validate input and to canonicalize numbers
to [E.164](https://en.wikipedia.org/wiki/E.164) format. The default
region is assumed to be "US". This is hard coded but could be made
configurable easily.
See also the
[javadocs](http://javadoc.io/doc/com.googlecode.libphonenumber/libphonenumber/).


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


### Running Tests

`lein test` will run the (admittedly small) test suite.


### Building and Running Uberjars

`lein ring uberjar` will build a standalone executable jar file in the
`target` directory. From that directory, run

    $ java -jar rest-exercise-0.1.0-standalone.jar

to start the service. The `PORT` environment variable is effective
when running the standalone service as well.


## Notes

### Extra Endpoint

An additional endpoint not specified in the requirements has been
added: `/number/<number>/<context>` will return the JSON
representation of the stored entity with the given `<number>` and
`<context>`. This endpoint eases testing a bit and provides a
convenient URL to return in `Location` headers.

The `Location` header is set for two types of POST requests:

1. Successful creation of a new entity (with 201 status and the entity
   in the body).
2. Post of parameters exactly matching an already-existing entity
   (with 303 status and no body).

This behavior seems to be the general recommended behavior for REST
services.

### Query Notes

The `/query` endpoint accepts numbers in any format that
libphonenumber can parse. The region is assumed to be "US" when this
is applicable. Invalid numbers result in a 400 response with a plain
text message.

### POST notes

The `/number` endpoint accepts both JSON an form parameters. The
`Content-Type` header must be set appropriately.

All three parameters must be present and nonempty. Violations give a
400 response with a meaningful plain text message. This wasn't
strictly specified, but seems reasonable.

Of course, invalid phone numbers also result in a 400.

## License

Copyright © 2017 Mike Conley

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
