-- :name save-user :<!
insert into "user" (id, subject, attributes)
values (:id, :subject, :attributes::jsonb)
on conflict (subject) do update
    set attributes = :attributes::jsonb returning id;

-- :name get-user :? :*
select id, subject, attributes
from "user"
where 1 = 1
/*~ (when (:ids params) */
  and id in (:v*:ids)
/*~ ) ~*/
;
