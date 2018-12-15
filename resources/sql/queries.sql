-- :name find-territories :? :*
SELECT id, number, address, region, ST_AsText(location) AS location
FROM territory;

-- :name find-regions :? :*
SELECT id, name, minimap_viewport, congregation, subregion, ST_AsText(location) AS location
FROM region;
