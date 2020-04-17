-- field :territory/subregion -> :territory/region
update event
set data = (data
    - 'territory/subregion')
    || jsonb_build_object('territory/region', data -> 'territory/subregion')
where data ->> 'event/type' = 'territory.event/territory-defined';

-- event :subregion.event/subregion-defined -> :region.event/region-defined
-- field :subregion/id -> :region/id
-- field :subregion/name -> :region/name
-- field :subregion/location -> :region/location
update event
set data = (data
    - 'subregion/id'
    - 'subregion/name'
    - 'subregion/location')
    || jsonb_build_object('event/type', 'region.event/region-defined',
                          'region/id', data -> 'subregion/id',
                          'region/name', data -> 'subregion/name',
                          'region/location', data -> 'subregion/location')
where data ->> 'event/type' = 'subregion.event/subregion-defined';

-- event :subregion.event/subregion-deleted -> :region.event/region-deleted
-- field :subregion/id -> :region/id
update event
set data = (data
    - 'subregion/id')
    || jsonb_build_object('event/type', 'region.event/region-deleted',
                          'region/id', data -> 'subregion/id')
where data ->> 'event/type' = 'subregion.event/subregion-deleted';
