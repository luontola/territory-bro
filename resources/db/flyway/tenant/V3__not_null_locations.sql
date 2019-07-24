delete
from territory
where location is null;

alter table territory
    alter column location set not null;

delete
from congregation_boundary
where location is null;

alter table congregation_boundary
    alter column location set not null;

delete
from subregion
where location is null;

alter table subregion
    alter column location set not null;

delete
from card_minimap_viewport
where location is null;

alter table card_minimap_viewport
    alter column location set not null;
