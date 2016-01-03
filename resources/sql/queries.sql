-- name: -create-territory!
INSERT INTO territory (id, number, address, region, location)
VALUES (nextval('territory_id_seq'), :number, :address, :region, ST_GeomFromGeoJSON(:location));

-- name: delete-all-territories!
DELETE FROM territory;

-- name: find-territories
SELECT
  t.id,
  t.number,
  t.address,
  t.region,
  ST_AsText(t.location)                                   AS location,
  ST_AsText(ST_Centroid(t.location :: GEOMETRY))          AS center,
  ST_AsText(ST_Multi(ST_Collect(r.location :: GEOMETRY))) AS "region-map"
FROM
  territory AS t,
  region AS r
WHERE ST_Intersects(t.location, r.location)
GROUP BY t.id;

-- name: find-regions
SELECT
  id,
  name,
  ST_AsText(location) AS location
FROM region;
