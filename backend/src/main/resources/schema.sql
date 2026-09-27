create table if not exists items (
  id                       integer primary key,
  name                     text    not null,
  examine                  text,
  members                  integer not null check (members in (0, 1)),
  low_alchemy              integer,
  high_alchemy             integer,
  buy_limit                integer,
  value                    integer,
  icon                     text,
  high_price               integer,
  high_price_time          integer,
  low_price                integer,
  low_price_time           integer,
  average_high_price_5m    real,
  high_price_volume_5m     integer,
  average_low_price_5m     real,
  low_price_volume_5m      integer,
  five_minute_timestamp    integer,
  updated_at               text    not null
);

create index if not exists idx_items_name on items (name);
