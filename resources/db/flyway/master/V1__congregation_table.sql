create table congregation
(
    id          uuid primary key,
    name        text        not null,
    schema_name varchar(63) not null
);
