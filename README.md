# Territory Bro

Territory Bro is a tool for managing territory cards in the congregations of Jehovah's Witnesses.

For more information, see <http://territorybro.com>


## Running

Install [PostgreSQL](http://www.postgresql.org/) or [Docker](https://www.docker.com/)

Install [Leiningen](https://github.com/technomancy/leiningen)

Start the database:

    docker-compose up -d db

Run database migrations:

    lein migratus

Start the application:

    lein run


## License

Copyright © 2015-2017, [Esko Luontola](http://luontola.fi)

This software is released under the [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).
