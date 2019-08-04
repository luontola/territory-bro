-- :name create-gis-user :!
insert into gis_user (congregation, "user", username, password)
values (:congregation, :user, :username, :password);

-- :name delete-gis-user :!
delete from gis_user
where congregation = :congregation
  and "user" = :user;
