create table stream
(
    stream_id    uuid primary key,
    entity_type  varchar(100)
        check ( entity_type in ('card-minimap-viewport',
                                'congregation',
                                'congregation-boundary',
                                'region',
                                'share',
                                'territory')),
    congregation uuid,
    gis_schema   varchar(100),
    gis_table    varchar(100)
);

insert into stream (stream_id, entity_type, congregation)
select stream_id,
       substring(data ->> 'event/type' from '^[^.]+'),
       (data ->> 'congregation/id')::uuid
from event
where stream_revision = 1;

alter table event
    add foreign key (stream_id) references stream (stream_id);

with congregation as (
    select data ->> 'congregation/id' as id,
           data ->> 'congregation/schema-name' as gis_schema
    from event
    where data ->> 'event/type' = 'congregation.event/congregation-created'
),
     gis_territory as (
         select distinct stream_id,
                         'territory' as gis_table,
                         data ->> 'congregation/id' as congregation
         from event
         where data ->> 'event/type' = 'territory.event/territory-defined'
     ),
     gis_subregion as (
         select distinct stream_id,
                         'subregion' as gis_table,
                         data ->> 'congregation/id' as congregation
         from event
         where data ->> 'event/type' = 'region.event/region-defined'
     ),
     gis_congregation_boundary as (
         select distinct stream_id,
                         'congregation_boundary' as gis_table,
                         data ->> 'congregation/id' as congregation
         from event
         where data ->> 'event/type' = 'congregation-boundary.event/congregation-boundary-defined'
     ),
     gis_card_minimap_viewport as (
         select distinct stream_id,
                         'card_minimap_viewport' as gis_table,
                         data ->> 'congregation/id' as congregation
         from event
         where data ->> 'event/type' = 'card-minimap-viewport.event/card-minimap-viewport-defined'
     ),
     gis as (
         select *
         from gis_territory
         union
         select *
         from gis_subregion
         union
         select *
         from gis_congregation_boundary
         union
         select *
         from gis_card_minimap_viewport
     ),
     updates as (
         select gis.stream_id, gis.gis_table, congregation.gis_schema
         from gis
              join congregation on congregation.id = gis.congregation
     )
update stream
set gis_table  = updates.gis_table,
    gis_schema = updates.gis_schema
from updates
where stream.stream_id = updates.stream_id;
