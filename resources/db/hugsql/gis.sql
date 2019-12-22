-- :name get-gis-changes :? :*
select id, schema, "table", op, "user", time, old, new
from gis_change_log
where 1 = 1
/*~ (when (contains? params :since) */
  and id > :since
/*~ ) ~*/
order by id;
