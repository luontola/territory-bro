create unique index event_share_key_u
    on event ((data ->> 'share/key'))
    where data ->> 'event/type' = 'share.event/share-created';
