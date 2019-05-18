create trigger territory_change_log
    after insert or update or delete
    on territory
    for each row
execute function ${masterSchema}.append_gis_change_log();

create trigger congregation_boundary_change_log
    after insert or update or delete
    on congregation_boundary
    for each row
execute function ${masterSchema}.append_gis_change_log();

create trigger subregion_change_log
    after insert or update or delete
    on subregion
    for each row
execute function ${masterSchema}.append_gis_change_log();

create trigger card_minimap_viewport_change_log
    after insert or update or delete
    on card_minimap_viewport
    for each row
execute function ${masterSchema}.append_gis_change_log();
