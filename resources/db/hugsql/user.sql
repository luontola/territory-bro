-- :name create-user :!
insert into "user" (id, subject, attributes)
values (:id, :subject, :attributes::jsonb);
