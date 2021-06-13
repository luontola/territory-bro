create trigger territory_validation
    before insert or update
    on territory
    for each row
execute function ${masterSchema}.validate_gis_change();

create trigger congregation_boundary_validation
    before insert or update
    on congregation_boundary
    for each row
execute function ${masterSchema}.validate_gis_change();

create trigger subregion_validation
    before insert or update
    on subregion
    for each row
execute function ${masterSchema}.validate_gis_change();

create trigger card_minimap_viewport_validation
    before insert or update
    on card_minimap_viewport
    for each row
execute function ${masterSchema}.validate_gis_change();
