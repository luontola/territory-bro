create table gis_change_log
(
    id      bigserial primary key,
    schema  varchar(63) not null,
    "table" varchar(63) not null,
    op      varchar(8)  not null,
    "user"  varchar(63) not null,
    time    timestamptz not null,
    old     jsonb,
    new     jsonb
);
