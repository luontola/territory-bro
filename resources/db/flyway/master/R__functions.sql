create or replace function simplify_large_geometry() returns trigger as
$$
declare
    TOLERANCE_1m constant double precision := 0.000009; -- about 1 meter in degrees
    TOLERANCE_100km constant double precision := 1.0; -- 111 km in degrees (at the equator)
    EPSILON constant double precision := TOLERANCE_1m / 1000;
    min_tolerance double precision := EPSILON;
    max_tolerance double precision := TOLERANCE_100km;
    tolerance double precision;
    TARGET_SIZE constant integer := 20000;
    geom_size integer;
    simplified_geom geometry;
begin
    geom_size := public.ST_MemSize(new.location::geometry);
--     RAISE WARNING 'start geom_size: %', geom_size;

    if geom_size > TARGET_SIZE then
        -- binary search for the optimal simplification tolerance
        -- to produce a geometry slightly smaller than target_size
        while (max_tolerance - min_tolerance > EPSILON) and
              (geom_size > TARGET_SIZE or geom_size < TARGET_SIZE * 0.95)
            loop
                if tolerance is null then
                    tolerance = TOLERANCE_1m; -- optimistically assume that only a little simplification is needed
                else
                    tolerance := (min_tolerance + max_tolerance) / 2;
                end if;

                simplified_geom := public.ST_SimplifyVW(new.location::geometry, tolerance);
                geom_size := public.ST_MemSize(simplified_geom);
--                 RAISE WARNING 'try geom_size: %, tolerance: %', geom_size, tolerance;

                if geom_size > TARGET_SIZE then
                    min_tolerance := tolerance;
                else
                    max_tolerance := tolerance;
                end if;
            end loop;

        new.location := public.ST_MakeValid(simplified_geom);
--         RAISE WARNING 'final geom_size: %', geom_size;
    end if;

    return new;
end;
$$ language plpgsql security definer;


create or replace function validate_gis_change() returns trigger as
$$
declare
    old_stream_id uuid;
    old_gis_schema varchar;
    old_gis_table varchar;
begin
    loop
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

        -- ID is already in use by another stream, so generate a new ID
        new.id = gen_random_uuid();
    end loop;
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
