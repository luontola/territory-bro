--  Copyright © 2015-2020 Esko Luontola
--  This software is released under the Apache License 2.0.
--  The license text is at http://www.apache.org/licenses/LICENSE-2.0

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
