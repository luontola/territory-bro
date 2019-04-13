-- :name find-foos :? :*
select foo_id, name
from foo;

-- :name create-foo :<!
insert into foo (name)
values (:name) returning foo_id;
