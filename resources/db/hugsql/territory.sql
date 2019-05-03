-- :name get-territories :? :*
select id, number, addresses, subregion, ST_AsText(location) AS location
from territory
where 1 = 1
/*~ (when (:ids params) */
  and id in (:v*:ids)
/*~ ) ~*/
;

-- :name create-territory :!
insert into territory (id, number, addresses, subregion, location)
values (:id, :number, :addresses, :subregion, ST_GeomFromText(:location));
