# Price data

Flip Finder's prices, volumes and buy limits come from the RuneScape Wiki's
[real-time prices API](https://oldschool.runescape.wiki/w/RuneScape:Real-time_Prices),
the data behind <https://prices.runescape.wiki/osrs>. The backend calls its v2
API at `https://prices.runescape.wiki/api/v2/osrs`:

- `/mapping`: every tradeable item's name, examine text, alchemy values, store
  value, icon and four-hour buy limit.
- `/latest`: each item's most recent instant-buy (high) and instant-sell (low)
  price and when it traded.
- `/5m`: average prices and traded volumes for the latest five-minute window.

It makes these three bulk requests at startup and every five minutes, never
one request per item. At the time of writing `/mapping` lists 4,662 items and
`/latest` prices 4,539 of them: items never seen trading are left out, and
five-minute coverage depends on how busy each window is.

## API rules

The wiki's documentation sets these constraints:

- There is no fixed rate limit, but sustained large requests every second can
  be blocked, so bulk endpoints are preferred over per-item requests.
- Requests need a descriptive `User-Agent`; generic ones from Java, curl and
  similar clients may be blocked. The backend's is set in
  [`application.properties`](../backend/src/main/resources/application.properties).
- Either side of an item's price can be `null` when that kind of trade has not
  been seen.
- Prices can exceed the 32-bit integer range and averages can be decimals, so
  the database stores prices and volumes as 64-bit integers and averages as
  floating point.

The maintainers ask anyone running a real-time pipeline to join the RuneScape
Wiki Discord's `#api-discussion` channel for maintenance and breaking-change
announcements.

## Buy limits

The `limit` in `/mapping` is the same data as the wiki's
[Grand Exchange buying limits](https://oldschool.runescape.wiki/w/Grand_Exchange/Buying_limits)
page. About 500 items have no documented limit. Flip Finder does not estimate
their profit, because assuming no limit would overstate it.

## Grand Exchange tax

The [Grand Exchange](https://oldschool.runescape.wiki/w/Grand_Exchange)
article sets out the tax: the seller pays 2% of the sale price, rounded down
and capped at 5M per item, so sales under 50 gp pay nothing. A short list of
items is exempt. The rules and exempt item ids are in
[`FlipCalculator.java`](../backend/src/main/java/com/flipfinder/service/FlipCalculator.java).
