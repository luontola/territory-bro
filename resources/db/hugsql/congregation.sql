-- :name create-congregation :!
insert into congregation (id, name, schema_name)
values (:id, :name, :schema_name);
