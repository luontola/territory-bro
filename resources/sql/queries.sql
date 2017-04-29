-- name: -create-territory!
INSERT INTO territory (id, number, address, region, location)
VALUES (nextval('territory_id_seq'), :number, :address, :region, ST_GEOMFROMGEOJSON( :location));

-- name: delete-all-territories!
DELETE FROM territory;

-- name: -count-territories
SELECT count(*)
FROM territory;

-- name: find-territories
SELECT
  id,
  number,
  address,
  region,
  ST_AsText(location) AS location
FROM
  territory;

-- name: -create-region!
INSERT INTO region (id, name, minimap_viewport, congregation, subregion, location)
VALUES (nextval('region_id_seq'), :name, :minimap_viewport, :congregation, :subregion, ST_GEOMFROMGEOJSON( :location));

-- name: delete-all-regions!
DELETE FROM region;

-- name: -count-regions
SELECT count(*)
FROM region;

-- name: find-regions
SELECT
  id,
  name,
  minimap_viewport,
  congregation,
  subregion,
  ST_AsText(location) AS location
FROM region;
