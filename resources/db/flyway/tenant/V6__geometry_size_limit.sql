alter table territory
    add constraint location_size check ( public.ST_MemSize(location::geometry) < 30000 );

alter table congregation_boundary
    add constraint location_size check ( public.ST_MemSize(location::geometry) < 30000 );

alter table subregion
    add constraint location_size check ( public.ST_MemSize(location::geometry) < 30000 );

alter table card_minimap_viewport
    add constraint location_size check ( public.ST_MemSize(location::geometry) < 30000 );
