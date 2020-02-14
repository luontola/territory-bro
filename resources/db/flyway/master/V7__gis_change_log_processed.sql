--  Copyright © 2015-2020 Esko Luontola
--  This software is released under the Apache License 2.0.
--  The license text is at http://www.apache.org/licenses/LICENSE-2.0

alter table gis_change_log
    add column processed boolean not null default false;

create index gis_change_log_unprocessed_idx on gis_change_log (id)
    where processed = false;
