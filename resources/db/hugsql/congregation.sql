-- :name get-congregations :? :*
select id, name, schema_name
from congregation
where 1 = 1
/*~ (when (:ids params) */
  and id in (:v*:ids)
/*~ ) ~*/
/*~ (when (:user params) */
  and :user in (select "user" from congregation_access m where m.congregation = id)
/*~ ) ~*/
;

-- :name create-congregation :!
insert into congregation (id, name, schema_name)
values (:id, :name, :schema_name);

---- User access

-- :name grant-access :!
insert into congregation_access ("user", congregation)
values (:user, :congregation);

-- :name revoke-access :!
delete from congregation_access
where "user" = :user
  and congregation = :congregation;
