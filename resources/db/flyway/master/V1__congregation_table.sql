create table congregation
(
    id          uuid primary key,
    name        varchar(1000) not null,
    schema_name varchar(63)   not null
);
