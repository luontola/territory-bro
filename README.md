# Territory Bro

Territory Bro is a tool for managing territory cards in the congregations of Jehovah's Witnesses.

For more information, see <https://territorybro.com>


## Running

Install [PostgreSQL](https://www.postgresql.org/) or [Docker](https://www.docker.com/)

Install [Leiningen](https://github.com/technomancy/leiningen)

Install [Node.js](https://nodejs.org/) and [Yarn](https://yarnpkg.com/)

Start the database:

    docker-compose up -d db

Run database migrations:

    lein migratus

Start the application:

    lein run -- 8081

Start the web frontend:

    yarn install
    yarn start

The server will run at http://localhost:8080/


## License

Copyright © 2015-2017, [Esko Luontola](http://luontola.fi)

This software is released under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).
