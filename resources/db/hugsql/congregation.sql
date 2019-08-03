-- :name get-congregations :? :*
select id, name, schema_name
from congregation
where 1 = 1
/*~ (when (:ids params) */
  and id in (:v*:ids)
/*~ ) ~*/
;

-- :name create-congregation :!
insert into congregation (id, name, schema_name)
values (:id, :name, :schema_name);
