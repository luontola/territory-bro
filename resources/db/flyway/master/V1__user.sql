create table "user"
(
    id         uuid primary key,
    subject    varchar(1000) not null,
    attributes jsonb         not null
);

create unique index user_subject_u on "user" (subject);
