CREATE TABLE territory (
  id       SERIAL PRIMARY KEY NOT NULL,
  number   VARCHAR(100)       NOT NULL DEFAULT '',
  address  TEXT               NOT NULL DEFAULT '',
  region   VARCHAR(1000)      NOT NULL DEFAULT '',
  location GEOGRAPHY(POLYGON, 4326)
);
