create table publisher
(
    congregation uuid         not null,
    id           uuid         not null,
    name         varchar(100) not null,
    primary key (congregation, id)
);
