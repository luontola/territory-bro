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
    latest_revision bigint;
begin
    lock table event in share row exclusive mode;

    select coalesce(max(global_revision), 0) into latest_revision
    from event;

    new.global_revision = latest_revision + 1;
    return new;
end;
$$ language plpgsql;
