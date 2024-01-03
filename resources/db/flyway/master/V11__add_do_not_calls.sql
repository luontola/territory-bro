create table do_not_calls
(
    congregation  uuid        not null,
    territory     uuid        not null,
    do_not_calls  text        not null,
    last_modified timestamptz not null,
    primary key (congregation, territory)
);
