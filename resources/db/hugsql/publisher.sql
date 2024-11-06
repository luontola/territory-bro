-- :name list-publishers :? :*
select congregation, id, name
from publisher
where congregation = :congregation
order by name
/*~ (when (:for-update? params) */
    for update
/*~ ) ~*/
;

-- :name save-publisher :! :n
insert into publisher (congregation, id, name)
values (:congregation, :id, :name)
on conflict (congregation, id) do update
    set name = :name;
