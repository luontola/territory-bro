-- :name -create-territory! :! :n
INSERT INTO territory (id, number, address, region, location)
VALUES (nextval('territory_id_seq'), :number, :address, :region, ST_GEOMFROMGEOJSON( :location));

-- :name delete-all-territories! :!
DELETE FROM territory;

-- :name -count-territories :? :1
SELECT count(*)
FROM territory;

-- :name find-territories :? :n
SELECT
  id,
  number,
  address,
  region,
  ST_AsText(location) AS location
FROM
  territory;

-- :name -create-region! :! :1
INSERT INTO region (id, name, minimap_viewport, congregation, subregion, location)
VALUES (nextval('region_id_seq'), :name, :minimap_viewport, :congregation, :subregion, ST_GEOMFROMGEOJSON( :location));

-- :name delete-all-regions! :! :n
DELETE FROM region;

-- :name -count-regions :? :1
SELECT count(*)
FROM region;

-- :name find-regions :? :n
SELECT
  id,
  name,
  minimap_viewport,
  congregation,
  subregion,
  ST_AsText(location) AS location
FROM region;
