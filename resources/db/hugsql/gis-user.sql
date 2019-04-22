-- :name create-gis-user :!
insert into gis_user (congregation, "user", username, password)
values (:congregation, :user, :username, :password);

-- :name delete-gis-user :!
delete from gis_user
where congregation = :congregation
  and "user" = :user;

-- :name get-gis-users :? :*
select congregation, "user", username, password
from gis_user
where 1 = 1
/*~ (when (:congregation params) */
  and congregation = :congregation
/*~ ) ~*/
/*~ (when (:user params) */
  and "user" = :user
/*~ ) ~*/
;
