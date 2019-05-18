-- :name read-stream :? :*
select stream_id, stream_revision, global_revision, data
from event
where stream_id = :stream
  and stream_revision > :since
order by stream_revision;

-- :name read-all-events :? :*
select stream_id, stream_revision, global_revision, data
from event
where global_revision > :since
order by global_revision;

-- :name save-event :!
insert into event (stream_id, stream_revision, data)
values (:stream, :stream_revision, :data);
