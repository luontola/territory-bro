create or replace function append_gis_change_log() returns trigger as
$fn$
begin
    -- make sure that the IDs are committed in monotonically increasing order
    lock table gis_change_log in share row exclusive mode;

    insert into gis_change_log (schema, "table", op, "user", time, new)
    select tg_table_schema,
           tg_table_name,
           tg_op,
           current_user,
           now(),
           jsonb_set(row_to_json(new)::jsonb, '{location}', to_jsonb(ST_AsText(new.location)));

    return null;
end
$fn$ language plpgsql;
