create or replace function append_gis_change_log() returns trigger as
$$
declare
    latest_id bigint;
begin
    lock table gis_change_log in share row exclusive mode;

    select coalesce(max(id), 0) into latest_id
    from gis_change_log;

    insert into gis_change_log (id, schema, "table", op, "user", time, old, new)
    select latest_id + 1,
           tg_table_schema,
           tg_table_name,
           tg_op,
           current_user,
           now(),
           case tg_op
               when 'INSERT' then null
               else jsonb_set(row_to_json(old)::jsonb, '{location}', to_jsonb(ST_AsText(old.location)))
               end,
           case tg_op
               when 'DELETE' then null
               else jsonb_set(row_to_json(new)::jsonb, '{location}', to_jsonb(ST_AsText(new.location)))
               end;

    return null;
end
$$ language plpgsql security definer;

create or replace function prepare_new_event() returns trigger as
$$
declare
    latest_global_revision bigint;
    latest_stream_revision integer;
begin
    lock table event in share row exclusive mode;

    select coalesce(max(global_revision), 0) into latest_global_revision
    from event;

    select coalesce(max(stream_revision), 0) into latest_stream_revision
    from event
    where stream_id = new.stream_id;

    if new.stream_revision != latest_stream_revision + 1 then
        raise exception 'tried to insert stream revision % but it should have been %',
            new.stream_revision,
            latest_stream_revision + 1
            using errcode = 'serialization_failure',
                hint = 'The transaction might succeed if retried.';
    end if;

    new.global_revision = latest_global_revision + 1;
    return new;
end
$$ language plpgsql;
