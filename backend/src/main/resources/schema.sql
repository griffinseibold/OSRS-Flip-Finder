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

-- The latest data the RuneLite plugin sent about each account (homelab only).
create table if not exists runelite_accounts (
  account_hash  integer primary key,
  display_name  text,
  snapshot      text    not null,
  received_at   text    not null
);

-- Trading history from the RuneScape Wiki (homelab only): each item's average
-- prices and volumes per five-minute or hourly bucket, kept for a few days or
-- weeks so the chat can tell whether today's trading is typical.
create table if not exists price_history (
  step            integer not null,  -- bucket length in seconds: 300 or 3600
  item_id         integer not null,
  timestamp       integer not null,  -- bucket start, in epoch seconds
  avg_high_price  integer,
  high_volume     integer not null,
  avg_low_price   integer,
  low_volume      integer not null,
  primary key (step, item_id, timestamp)
) without rowid;

-- Every bucket imported. An item missing from an imported bucket did not trade
-- in it, which matters as much as the buckets it did trade in.
create table if not exists price_history_buckets (
  step       integer not null,
  timestamp  integer not null,
  items      integer not null,
  primary key (step, timestamp)
) without rowid;
