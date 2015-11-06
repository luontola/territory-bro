-- name: create-territory!
INSERT INTO territory (id, name, address, area)
VALUES (nextval('territory_id_seq'), :name, :address, :area);
