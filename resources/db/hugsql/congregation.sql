-- :name get-congregations :? :*
select id, name, schema_name
from congregation;

-- :name create-congregation :!
insert into congregation (id, name, schema_name)
values (:id, :name, :schema_name);
