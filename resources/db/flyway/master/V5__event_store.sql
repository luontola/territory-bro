create table event
(
    stream_id       uuid          not null,
    stream_revision integer       not null,
    global_revision bigint unique not null,
    data            jsonb         not null,
    primary key (stream_id, stream_revision)
);

-- placeholder for database initialization
create function prepare_new_event() returns trigger as 'begin end' language plpgsql;

create trigger event_insert
    before insert
    on event
    for each row
execute function prepare_new_event();
