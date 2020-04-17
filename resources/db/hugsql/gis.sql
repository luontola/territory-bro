--  Copyright © 2015-2020 Esko Luontola
--  This software is released under the Apache License 2.0.
--  The license text is at http://www.apache.org/licenses/LICENSE-2.0

-- :name get-congregation-boundaries :? :*
select id, ST_AsText(location) AS location
from congregation_boundary;

-- :name create-congregation-boundary :!
insert into congregation_boundary (id, location)
values (:id, ST_Multi(ST_GeomFromText(:location)));


-- TODO: rename subregion to region the next time other incompatible changes are needed

-- :name get-regions :? :*
select id, name, ST_AsText(location) AS location
from subregion;

-- :name create-region :!
insert into subregion (id, name, location)
values (:id, :name, ST_Multi(ST_GeomFromText(:location)));


-- :name get-card-minimap-viewports :? :*
select id, ST_AsText(location) AS location
from card_minimap_viewport;

-- :name create-card-minimap-viewport :!
insert into card_minimap_viewport (id, location)
values (:id, ST_GeomFromText(:location));


-- TODO: rename subregion to region the next time other incompatible changes are needed

-- :name get-territories :? :*
select id, number, addresses, subregion, meta, ST_AsText(location) AS location
from territory
where 1 = 1
/*~ (when (contains? params :ids) */
  and id = any (array[:v*:ids]::uuid[])
/*~ ) ~*/
;

-- :name create-territory :!
insert into territory (id, number, addresses, subregion, meta, location)
values (:id, :number, :addresses, :subregion, :meta, ST_Multi(ST_GeomFromText(:location)));


-- :name get-gis-changes :? :*
select id, schema, "table", op, "user", time, old, new, processed, replacement_id
from gis_change_log
where 1 = 1
/*~ (when (contains? params :since) */
  and id > :since
/*~ ) ~*/
/*~ (when (contains? params :processed?) */
  and processed = :processed?
/*~ ) ~*/
order by id
/*~ (when (contains? params :limit) */
limit :limit
/*~ ) ~*/
;

-- :name mark-changes-processed :!
update gis_change_log
set processed = true
where id = any (array[:v*:ids]::bigint[]);

--  Copyright © 2015-2020 Esko Luontola
--  This software is released under the Apache License 2.0.
--  The license text is at http://www.apache.org/licenses/LICENSE-2.0

-- :name replace-id-of-entity :!
update :i:schema_table
set id = :new_id
where id = :old_id;

-- :name replace-id-of-changes :!
update gis_change_log
set replacement_id = :new_id
where schema = :schema
  and "table" = :table
  and ((new ->> 'id')::uuid = :old_id
    or (old ->> 'id')::uuid = :old_id)
  and replacement_id is null
  and processed is false;
