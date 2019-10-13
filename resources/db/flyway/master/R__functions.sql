create or replace function append_gis_change_log() returns trigger as
$$
declare
    latest_id bigint;
begin
    lock table ${masterSchema}.gis_change_log in share row exclusive mode;

    select coalesce(max(id), 0)
    into latest_id
    from ${masterSchema}.gis_change_log;

    insert into ${masterSchema}.gis_change_log (id, schema, "table", op, "user", time, old, new)
    select latest_id + 1,
           tg_table_schema,
           tg_table_name,
           tg_op,
           session_user,
           now(),
           case tg_op
               when 'INSERT' then null
               else jsonb_set(row_to_json(old)::jsonb, '{location}', to_jsonb(public.ST_AsText(old.location)))
               end,
           case tg_op
               when 'DELETE' then null
               else jsonb_set(row_to_json(new)::jsonb, '{location}', to_jsonb(public.ST_AsText(new.location)))
               end;

    return null;
end
$$ language plpgsql security definer;

create or replace function prepare_new_event() returns trigger as
$$
declare
    next_global_revision bigint;
    next_stream_revision integer;
begin
    lock table ${masterSchema}.event in share row exclusive mode;

    select coalesce(max(global_revision), 0) + 1
    into next_global_revision
    from ${masterSchema}.event;

    select coalesce(max(stream_revision), 0) + 1
    into next_stream_revision
    from ${masterSchema}.event
    where stream_id = new.stream_id;

    if new.stream_revision is null then
        new.stream_revision = next_stream_revision;
    end if;

    if new.stream_revision != next_stream_revision then
        raise exception 'tried to insert stream revision % but it should have been %',
            new.stream_revision,
            next_stream_revision
            using errcode = 'serialization_failure',
                hint = 'The transaction might succeed if retried.';
    end if;

    new.global_revision = next_global_revision;
    return new;
end
$$ language plpgsql;
