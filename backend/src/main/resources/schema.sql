create table if not exists records (
  id                integer primary key,
  data              text    not null,
  source_updated_at text    not null,
  updated_at        text    not null,
  name              text    generated always as (json_extract(data, '$.name')) stored,
  examine           text    generated always as (json_extract(data, '$.examine')) stored,
  high_alchemy      integer generated always as (json_extract(data, '$.HighAlchemy')) stored,
  low_alchemy       integer generated always as (json_extract(data, '$.LowAlchemy')) stored,
  limit_qty         integer generated always as (json_extract(data, '$.limit')) stored,
  price             integer generated always as (json_extract(data, '$.price')) stored,
  last              integer generated always as (json_extract(data, '$.last')) stored
);