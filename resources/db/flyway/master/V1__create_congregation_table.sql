create table foo
(
    foo_id serial primary key,
    name   text not null
);
create table congregation
(
    congregation_id serial primary key,
    name            text not null,
    schema_name     text not null
);

