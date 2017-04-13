-- name: -create-territory!
INSERT INTO territory (id, number, address, region, location)
VALUES (nextval('territory_id_seq'), :number, :address, :region, ST_GeomFromGeoJSON(:location));

-- name: delete-all-territories!
DELETE FROM territory;

-- name: -count-territories
SELECT count(*)
FROM territory;

-- name: find-territories-with-regions
SELECT
  t.id,
  t.number,
  t.address,
  t.region,
  ST_AsText(t.location)                                              AS location,
  ST_AsText(ST_Centroid(t.location :: GEOMETRY))                     AS center,
  ST_AsText(ST_Multi(ST_Collect(viewport.location :: GEOMETRY)))     AS minimap_viewport,
  ST_AsText(ST_Multi(ST_Collect(congregation.location :: GEOMETRY))) AS congregation,
  ST_AsText(ST_Multi(ST_Collect(subregion.location :: GEOMETRY)))    AS subregions
FROM
  territory AS t
  LEFT OUTER JOIN region AS viewport
    ON ST_Intersects(t.location, viewport.location)
       AND viewport.minimap_viewport = TRUE
  LEFT OUTER JOIN region AS congregation
    ON ST_Intersects(t.location, congregation.location)
       AND congregation.congregation = TRUE
  LEFT OUTER JOIN region AS subregion
    ON ST_Intersects(t.location, subregion.location)
       AND subregion.subregion = TRUE
GROUP BY t.id
ORDER BY
  -- XXX: natural sorting hack based on http://stackoverflow.com/a/9482849/62130
  SUBSTRING(t.number FROM '^(\d+)') :: INTEGER,
  t.number;

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
VALUES (nextval('region_id_seq'), :name, :minimap_viewport, :congregation, :subregion, ST_GeomFromGeoJSON(:location));

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
