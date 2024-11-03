-- :name get-users :? :*
select id, subject, attributes
from "user"
where 1 = 1
/*~ (when (contains? params :ids) */
  and id = any (array[:v*:ids]::uuid[])
/*~ ) ~*/
/*~ (when (contains? params :subjects) */
  and subject = any (array[:v*:subjects]::text[])
/*~ ) ~*/
;

-- :name save-user :<!
insert into "user" (id, subject, attributes)
values (:id, :subject, :attributes::jsonb)
on conflict (subject) do update
    set attributes = :attributes::jsonb returning id;
