-- :name get-do-not-calls :? :1
select congregation, territory, do_not_calls, last_modified
from do_not_calls
where congregation = :congregation
  and territory = :territory
limit 1;

-- :name save-do-not-calls :! :n
insert into do_not_calls (congregation, territory, do_not_calls, last_modified)
values (:congregation, :territory, :do_not_calls, :last_modified)
on conflict on constraint do_not_calls_pkey
    do update set do_not_calls  = :do_not_calls,
                  last_modified = :last_modified;

-- :name delete-do-not-calls :! :n
delete
from do_not_calls
where congregation = :congregation
  and territory = :territory;
