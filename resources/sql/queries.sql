-- name: -create-territory!
INSERT INTO territory (id, number, address, region, location)
VALUES (nextval('territory_id_seq'), :number, :address, :region, ST_GeomFromGeoJSON(:location));

-- name: delete-all-territories!
DELETE FROM territory;

-- name: find-territories
SELECT
  id,
  number,
  address,
  region,
  ST_AsText(location)                          AS location,
  ST_AsText(ST_Centroid(location :: GEOMETRY)) AS center
FROM territory;
