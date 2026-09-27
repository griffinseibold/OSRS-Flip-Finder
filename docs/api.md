# API

The web app is built on these HTTP endpoints. They are served on the same
port as the app: <http://localhost:8081> when run with Docker, and
<http://flipfinder.localhost:8080> on the homelab.

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/api/flips` | One page of items priced, filtered and sorted as flips |
| `GET` | `/api/items?page=0&size=25` | One page of Grand Exchange items by id |
| `PUT` | `/api/runelite/accounts/{accountHash}` | Report an account; the [RuneLite plugin](runelite-plugin.md) calls this (homelab only) |
| `GET` | `/api/runelite/accounts` | Every account the plugin has reported (homelab only) |
| `GET` | `/api/runelite/accounts/{accountHash}` | One account, with its buy limits still in effect (homelab only) |
| `GET` | `/v3/api-docs` | OpenAPI document |
| `GET` | `/swagger-ui.html` | Interactive Swagger UI |
| `GET` | `/actuator/health/liveness` | Kubernetes liveness check |
| `GET` | `/actuator/health/readiness` | Kubernetes readiness check |

## Flips

`/api/flips` accepts these query parameters, all optional. Values are
case-insensitive.

| Parameter | Default | Meaning |
| --- | --- | --- |
| `sort` | `estimatedProfit` | `estimatedProfit`, `potentialProfit`, `margin`, `roi`, `buyPrice`, `sellPrice`, `buyLimit`, `volume5m`, `lastTradeTime` or `name` |
| `direction` | `desc` (`asc` for `name`) | `asc` or `desc`; missing values always sort last |
| `page`, `size` | `0`, `25` | Zero-based page and page size, at most 200 |
| `basis` | `latest` | `latest` trade prices or `average5m` five-minute averages |
| `search` | | Case-insensitive part of the item name |
| `membership` | `all` | `all`, `f2p` or `members` |
| `maxTradeAgeMinutes` | `0` | Both sides must have traded this recently; `0` for any time |
| `minVolume5m` | `0` | Minimum items traded in the latest five-minute window |
| `budget` | | Coins available: hides items that cost more and limits quantity to what it buys |
| `account` | | Account hash from the [RuneLite plugin](runelite-plugin.md): see below |

For example, the best flips for a 10M budget among active members items:

```bash
curl 'http://localhost:8081/api/flips?budget=10000000&membership=members&minVolume5m=10&size=10'
```

Each result holds the `item` plus `buyPrice`, `sellPrice`, `tax`, `margin`,
`roi`, `quantity`, `potentialProfit`, `volume5m`, `fillableQuantity`,
`estimatedProfit`, `limitedBy` (`buyLimit`, `budget` or `volume`) and
`lastTradeTime`. The quantity and profit fields are `null` for items with no
known buy limit. The page also reports how many matching flips are profitable
or losing, the `topFlip`, the `budget` it applied, and when the most recent
trade happened.

With `account`, the flips are for that account as the RuneLite plugin last
reported it: its coins become the budget unless `budget` is given, a
free-to-play account only sees free-to-play items, and each item's buy limit
shrinks by what the account already bought in its current four-hour window.
Those items also show `alreadyBought` and `limitResetsAt`. An account the plugin
has never reported returns 404.

The estimate caps the quantity at what could trade in four hours at the pace
of the latest five-minute window: 48 times the slower of the two sides' volume.
[Using Flip Finder](using-flip-finder.md#the-columns) explains each field.

Every item is priced and sorted in memory on each request, because the sort
keys depend on the request's price basis and budget.

## RuneLite accounts

These endpoints exist only on the homelab, where the `homelab` profile turns
them on. The [plugin](runelite-plugin.md) `PUT`s the account's latest data
whenever it changes; each report replaces the last.

`GET /api/runelite/accounts/{accountHash}` returns the account's
`displayName`, `members`, `membershipDays`, `ironman`, `inventoryCoins`,
`bankCoins` and their total `coins` (`null` until the bank has been seen), its
Grand Exchange `geOffers`, and its `buyLimits`: each item bought in a window
that is still running, with its `limit`, how many were `bought`, how many
`remaining` and when it `resetsAt`. `capturedAt` is when the plugin read the
data.

## Items

`/api/items` returns the stored item data ordered by id: `name`, `examine`,
`members`, `lowAlchemy`, `highAlchemy`, `buyLimit`, `value`, `icon`, the latest
high and low prices with their trade times, and the five-minute average prices
and volumes. Missing values are `null`.
