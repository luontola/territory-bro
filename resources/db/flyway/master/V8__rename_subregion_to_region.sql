-- rename :territory/subregion -> :territory/region
update event
set data = jsonb_set(data - 'territory/subregion', '{territory/region}', data -> 'territory/subregion')
where data ->> 'event/type' = 'territory.event/territory-defined';


-- rename :subregion.event/subregion-defined -> :region.event/region-defined
update event
set data = jsonb_set(data, '{event/type}', '"region.event/region-defined"')
where data ->> 'event/type' = 'subregion.event/subregion-defined';

-- rename :subregion/id -> :region/id
update event
set data = jsonb_set(data - 'subregion/id', '{region/id}', data -> 'subregion/id')
where data ->> 'event/type' = 'region.event/region-defined';

-- rename :subregion/name -> :region/name
update event
set data = jsonb_set(data - 'subregion/name', '{region/name}', data -> 'subregion/name')
where data ->> 'event/type' = 'region.event/region-defined';

-- rename :subregion/location -> :region/location
update event
set data = jsonb_set(data - 'subregion/location', '{region/location}', data -> 'subregion/location')
where data ->> 'event/type' = 'region.event/region-defined';


-- rename :subregion.event/subregion-deleted -> :region.event/region-deleted
update event
set data = jsonb_set(data, '{event/type}', '"region.event/region-deleted"')
where data ->> 'event/type' = 'subregion.event/subregion-deleted';

-- rename :subregion/id -> :region/id
update event
set data = jsonb_set(data - 'subregion/id', '{region/id}', data -> 'subregion/id')
where data ->> 'event/type' = 'region.event/region-deleted';
