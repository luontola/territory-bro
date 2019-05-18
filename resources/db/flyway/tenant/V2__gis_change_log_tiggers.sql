create trigger territory_change_log
    after insert or update or delete
    on territory
    for each row
execute procedure ${masterSchema}.append_gis_change_log();
