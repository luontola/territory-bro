-- :name get-congregations :? :*
select id, name, schema_name
from congregation
where 1 = 1
/*~ (when (:ids params) */
  and id in (:v*:ids)
/*~ ) ~*/
/*~ (when (:user params) */
  and :user in (select "user" from congregation_membership m where m.congregation = id)
/*~ ) ~*/
;

-- :name create-congregation :!
insert into congregation (id, name, schema_name)
values (:id, :name, :schema_name);

-- :name get-members :? :*
select "user"
from congregation_membership
where congregation = :congregation;

-- :name add-member :!
insert into congregation_membership ("user", congregation)
values (:user, :congregation);

-- :name remove-member :!
delete from congregation_membership
where "user" = :user
  and congregation = :congregation;
