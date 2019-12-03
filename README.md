# Territory Bro

Territory Bro is a tool for managing territory cards in the congregations of Jehovah's Witnesses.

For more information, see <https://territorybro.com>

[![Travis CI](https://travis-ci.org/luontola/territory-bro.svg?branch=master)](https://travis-ci.org/luontola/territory-bro)

[Semaphore CI](https://luontola.semaphoreci.com/projects/territory-bro)


## Running

The easiest way to run this application is to use [Docker](https://www.docker.com/products/docker-desktop).

Start the database and run database migration scripts to initialize the database

    docker-compose pull
    docker-compose up -d db
    docker-compose run --rm api migrate

Start the application

    docker-compose up -d

The application will run at http://localhost:8080

Stop the application

    docker-compose stop

Stop the application and remove all data

    docker-compose down


## Developing

The tools for developing this project are
[Docker](https://www.docker.com/),
[Java 11 JDK](http://jdk.java.net/11/),
[Leiningen](https://github.com/technomancy/leiningen),
[Node.js](https://nodejs.org/) and
[Yarn](https://yarnpkg.com/).
It might also be useful to have the [PostgreSQL](https://www.postgresql.org/) command line tools, even if you run the database with Docker.

Start the database

    docker-compose up -d db

Run database migrations

    lein run migrate

Start the API backend

    lein run

Start the web frontend

    yarn install
    yarn start

The application will run at http://localhost:8080

Run tests

    lein test
    yarn run flow check
    yarn run test

Run tests automatically on change

    lein autotest
    yarn run autotest

Download dependency sources

    lein pom
    mvn dependency:sources

Produce canonical XML for better diffs

    xmllint --c14n11 example.qgs > resources/template-territories.qgs


## License

Copyright © 2015-2018, [Esko Luontola](http://www.luontola.fi)

This software is released under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).
