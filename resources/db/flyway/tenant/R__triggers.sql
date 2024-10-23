-- territory

create or replace trigger territory_1_simplify
    before insert or update
    on territory
    for each row
execute function ${masterSchema}.simplify_large_geometry();

create or replace trigger territory_2_validation
    before insert or update
    on territory
    for each row
execute function ${masterSchema}.validate_gis_change();

create or replace trigger territory_3_change_log
    after insert or update or delete
    on territory
    for each row
execute function ${masterSchema}.append_gis_change_log();

-- congregation_boundary

create or replace trigger congregation_boundary_1_simplify
    before insert or update
    on congregation_boundary
    for each row
execute function ${masterSchema}.simplify_large_geometry();

create or replace trigger congregation_boundary_2_validation
    before insert or update
    on congregation_boundary
    for each row
execute function ${masterSchema}.validate_gis_change();

create or replace trigger congregation_boundary_3_change_log
    after insert or update or delete
    on congregation_boundary
    for each row
execute function ${masterSchema}.append_gis_change_log();

-- subregion

create or replace trigger subregion_1_simplify
    before insert or update
    on subregion
    for each row
execute function ${masterSchema}.simplify_large_geometry();

create or replace trigger subregion_2_validation
    before insert or update
    on subregion
    for each row
execute function ${masterSchema}.validate_gis_change();

create or replace trigger subregion_3_change_log
    after insert or update or delete
    on subregion
    for each row
execute function ${masterSchema}.append_gis_change_log();

-- card_minimap_viewport

create or replace trigger card_minimap_viewport_1_simplify
    before insert or update
    on card_minimap_viewport
    for each row
execute function ${masterSchema}.simplify_large_geometry();

create or replace trigger card_minimap_viewport_2_validation
    before insert or update
    on card_minimap_viewport
    for each row
execute function ${masterSchema}.validate_gis_change();

create or replace trigger card_minimap_viewport_3_change_log
    after insert or update or delete
    on card_minimap_viewport
    for each row
execute function ${masterSchema}.append_gis_change_log();
