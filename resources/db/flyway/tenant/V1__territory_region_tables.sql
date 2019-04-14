create table territory
(
    id       serial primary key not null,
    number   varchar(100)       not null default '',
    address  text               not null default '',
    region   varchar(1000)      not null default '',
    location geography(polygon, 4326)
);

create table region
(
    id               serial primary key not null,
    name             varchar(1000)      not null default '',
    location         geography(polygon, 4326),
    minimap_viewport boolean            not null default false,
    congregation     boolean            not null default false,
    subregion        boolean            not null default false
);
