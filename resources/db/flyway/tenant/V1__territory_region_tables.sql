create table territory
(
    id        uuid primary key,
    number    varchar(100)  not null default '',
    addresses varchar(1000) not null default '',
    subregion varchar(1000) not null default '',
    location  geography(multipolygon, 4326)
);

create table congregation_boundary
(
    id       uuid primary key,
    location geography(multipolygon, 4326)
);

create table subregion
(
    id       uuid primary key,
    name     varchar(1000) not null default '',
    location geography(multipolygon, 4326)
);

create table card_minimap_viewport
(
    id       uuid primary key,
    location geography(polygon, 4326)
);
