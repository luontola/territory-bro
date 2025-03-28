# Territory Bro

Territory Bro is a web app for managing field service territories in the congregations of Jehovah's Witnesses.

For more information, see <https://territorybro.com>

[![Build Status](https://luontola.semaphoreci.com/badges/territory-bro/branches/main.svg?style=shields)](https://luontola.semaphoreci.com/projects/territory-bro)

## Running

The easiest way to run this application is to use [Docker](https://www.docker.com/products/docker-desktop).

Start the database

    docker compose pull
    docker compose up -d db

Start the application

    docker compose up -d app

The application will run at http://localhost:8080

Stop the application (does not remove the database volume)

    docker compose down

Remove the database volume

    docker compose down --volumes

## Developing

The tools for developing this project are
[Java JDK](https://www.oracle.com/java/technologies/downloads/),
[Leiningen](https://github.com/technomancy/leiningen),
[Node.js](https://nodejs.org/) and
[Docker](https://www.docker.com/). It might also be useful to have the [PostgreSQL](https://www.postgresql.org/) command
line tools, even if you run the database with Docker.

Install dependencies

    npm install

Build the frontend assets for the backend (once or automatically)

    npm run build
    npm run autobuild

Start the database

    docker compose up -d db

Start the backend, it will run at http://localhost:8080

    lein repl
    (start)

Restart the backend, reloading code changes

    (reset)

Run tests

    lein test
    npm run test

Run tests selectively

    lein kaocha fast
    lein kaocha slow
    lein kaocha e2e

Run tests automatically on change

    lein autotest
    npm run autotest

Upgrade dependencies

    lein ancient upgrade :all :check-clojure :no-tests
    npm run upgrade

    asdf install java latest:temurin-21
    asdf local java latest:temurin-21
    asdf global java latest:temurin-21

    asdf install nodejs latest
    asdf local nodejs latest
    asdf global nodejs latest

Download dependency sources

    lein pom
    mvn dependency:sources

Produce canonical XML for better diffs

    xmllint --c14n11 example.qgs > resources/template-territories.qgs

## License

Copyright © 2015-2024, [Esko Luontola](https://www.luontola.fi)

This software is released under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).
