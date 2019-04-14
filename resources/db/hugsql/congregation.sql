-- :name find-foos :? :*
select foo_id, name
from foo;

-- :name create-foo :<!
insert into foo (name)
values (:name) returning foo_id;

-- :name get-congregations :? :*
select congregation_id, name, schema_name
from congregation;

-- :name create-congregation :<!
insert into congregation (name, schema_name)
values (:name, :schema_name) returning congregation_id;