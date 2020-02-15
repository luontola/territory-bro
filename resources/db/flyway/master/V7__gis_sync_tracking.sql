alter table gis_change_log
    add column processed      boolean not null default false,
    add column replacement_id uuid    null;

create index gis_change_log_unprocessed_idx on gis_change_log (id)
    where processed = false;
