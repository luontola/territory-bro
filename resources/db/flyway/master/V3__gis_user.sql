create table gis_user
(
    congregation uuid         not null references congregation (id),
    "user"       uuid         not null references "user" (id),
    username     varchar(63)  not null,
    password     varchar(100) not null,
    primary key (congregation, "user")
);

create unique index gis_user_username_u on gis_user (username);
