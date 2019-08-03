drop table congregation_access;

alter table gis_user
    drop constraint gis_user_congregation_fkey;

drop table congregation;
