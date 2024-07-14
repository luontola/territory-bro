--  Copyright © 2015-2020 Esko Luontola
--  This software is released under the Apache License 2.0.
--  The license text is at http://www.apache.org/licenses/LICENSE-2.0

-- :name read-stream :? :*
select stream_id, stream_revision, global_revision, data::text
from event
where stream_id = :stream
  and stream_revision > :since
order by stream_revision;

-- :name read-all-events :? :*
select stream_id, stream_revision, global_revision, data::text
from event
where global_revision > :since
order by global_revision;

-- :name find-stream :? :*
select stream_id
from event
where stream_id = :stream
limit 1;

-- :name lock-events-table-for-writing :!
lock table event in share row exclusive mode;

-- :name save-event :<! :1
insert into event (stream_id, stream_revision, data)
values (:stream, :stream_revision, :data::jsonb)
returning global_revision, stream_revision;
