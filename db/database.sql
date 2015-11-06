CREATE TABLE territorybro.territory (
  id      SERIAL PRIMARY KEY NOT NULL,
  name    VARCHAR(1000)      NOT NULL,
  address TEXT               NOT NULL DEFAULT '',
  area    GEOGRAPHY(POLYGON, 4326)
);
