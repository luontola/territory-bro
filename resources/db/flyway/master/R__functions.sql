create or replace function validate_gis_change() returns trigger as
$$
declare
    old_stream_id uuid;
    old_gis_schema varchar;
    old_gis_table varchar;
begin
    select stream_id, gis_schema, gis_table
    into old_stream_id, old_gis_schema, old_gis_table
    from ${masterSchema}.stream
    where stream_id = new.id;

    if old_stream_id is null then
        -- updating an existing row or restoring a deleted row

        insert into ${masterSchema}.stream (stream_id, gis_schema, gis_table)
        values (new.id, tg_table_schema, tg_table_name);

        return new;
    end if;

    if old_gis_schema = tg_table_schema and
       old_gis_table = tg_table_name then
        -- inserting a new row

        return new;
    end if;

    raise exception 'duplicate key value violates unique constraint'
        using errcode = 'unique_violation',
            hint = ('ID ' || new.id || ' is already used in some other table and schema');
end
$$ language plpgsql security definer;

create or replace function append_gis_change_log() returns trigger as
$$
declare
    next_id bigint;
begin
    lock table ${masterSchema}.gis_change_log in share row exclusive mode;

    select coalesce(max(id), 0) + 1
    into next_id
    from ${masterSchema}.gis_change_log;

    insert into ${masterSchema}.gis_change_log (id, schema, "table", op, "user", time, old, new)
    select next_id,
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

    notify gis_change;

    return null;
end
$$ language plpgsql security definer;

create or replace function prepare_new_event() returns trigger as
$$
declare
    next_global_revision bigint;
    next_stream_revision integer;
    new_entity_type varchar;
    new_congregation uuid;
begin
    -- only one writer, but don't block readers
    lock table ${masterSchema}.event in share row exclusive mode;

    -- monotonically increasing revision numbers

    select coalesce(max(global_revision), 0) + 1
    into next_global_revision
    from ${masterSchema}.event;

    select coalesce(max(stream_revision), 0) + 1
    into next_stream_revision
    from ${masterSchema}.event
    where stream_id = new.stream_id;

    -- optimistic concurrency control

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

    -- populate stream table

    if next_stream_revision = 1 then
        new_entity_type = substring(new.data ->> 'event/type' from '^[^.]+');
        if new_entity_type not in ('card-minimap-viewport',
                                   'congregation',
                                   'congregation-boundary',
                                   'region',
                                   'share',
                                   'territory') then
            new_entity_type = null;
        end if;

        new_congregation = (new.data ->> 'congregation/id')::uuid;

        insert into stream (stream_id, entity_type, congregation)
        values (new.stream_id, new_entity_type, new_congregation)
        on conflict (stream_id) do update
            set entity_type  = new_entity_type,
                congregation = new_congregation;
    end if;

    return new;
end
$$ language plpgsql;
