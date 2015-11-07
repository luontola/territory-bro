-- name: -create-territory!
INSERT INTO territory (id, name, address, area)
VALUES (nextval('territory_id_seq'), :name, :address, ST_GeomFromGeoJSON(:area));

-- name: find-territories
SELECT
  id,
  name,
  address,
  ST_AsText(area) AS area
FROM territory;
