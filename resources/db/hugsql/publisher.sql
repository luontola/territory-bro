-- :name list-publishers :? :*
select congregation, id, name
from publisher
where congregation = :congregation
order by name;

-- :name get-publisher :? :1
select congregation, id, name
from publisher
where congregation = :congregation
  and id = :id;

-- :name save-publisher :! :n
insert into publisher (congregation, id, name)
values (:congregation, :id, :name)
on conflict (congregation, id) do update
    set name = :name;
