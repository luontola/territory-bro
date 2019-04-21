create table congregation
(
    id          uuid primary key,
    name        varchar(1000) not null,
    schema_name varchar(63)   not null
);

create table congregation_membership
(
    congregation uuid not null references congregation (id),
    "user"       uuid not null references "user" (id),
    primary key (congregation, "user")
);

create index congregation_membership_user_idx on congregation_membership ("user", congregation);
