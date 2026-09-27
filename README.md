# Old School RuneScape Flip Finder

This Spring Boot service downloads Old School RuneScape Grand Exchange data
from the RuneScape Wiki real-time price API, stores typed item fields in
SQLite, and exposes the items through a paginated HTTP API. A React web app,
served by the same service, ranks items by how much they could make or lose
when flipped.

[`schema.sql`](backend/src/main/resources/schema.sql) defines the database. The
default `local` profile keeps SQLite in memory, while the `homelab` profile
stores `/data/flipfinder.db` on a persistent volume. No database file is
committed to Git.

## Web app

The web app at `/` asks the API for one ranked page of flips at a time and
shows, for each item:

- **Buy at / sell at:** the latest instant-sell (low) and instant-buy (high)
  prices, where a flipper places buy and sell offers. A toggle switches to
  five-minute average prices instead.
- **Margin:** sell price minus Grand Exchange tax minus buy price, per item.
  The tax is 2% of the sale price, rounded down and capped at 5M per item;
  sales under 50 gp and a short list of exempt items (such as bonds) pay none.
  Losing flips are shown in red.
- **ROI**, **buy limit**, and **potential** profit: the margin across one
  four-hour buy limit, or as many items as your budget affords.
  Limits come from the price API's item mapping, which carries the wiki's
  [buying limits](https://oldschool.runescape.wiki/w/Grand_Exchange/Buying_limits)
  list. About 500 items have no documented limit; rather than treat them as
  unlimited, the app gives them no potential or estimated profit and ranks
  them last.
- **5-min volume:** items traded at the low and at the high price in the latest
  five-minute window. Buy offers fill from trades at the low price and sell
  offers from trades at the high price, so the slower side sets the pace. A bar
  shows how much of the quantity that pace fills in four hours.
- **Est. profit:** the margin across what could fill in four hours (48
  five-minute windows) at that pace, capped by the buy limit and budget,
  and labelled with whichever of the three is the cap. This is the default
  ranking. It assumes every trade at your price is yours, so treat it as an
  upper bound.

By default the list also hides items that have not traded on both sides within
the last hour or traded fewer than ten times in the last five minutes. Set a
**budget** (such as `10m`) to hide items that cost more than you have and
limit each flip to what you can afford. Filters, sorting, the budget and the
light or dark theme are remembered in the browser; the sun and moon button in
the header switches theme, which starts from the system setting. Selecting an
item shows every field the API returns for it, the four-hour estimate step by
step, and links to the OSRS Wiki.

## API

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/api/flips` | Return one page of items priced, filtered and sorted as flips |
| `GET` | `/api/items?page=0&size=25` | Return one page of Grand Exchange items by id |
| `GET` | `/v3/api-docs` | OpenAPI document |
| `GET` | `/swagger-ui.html` | Interactive Swagger UI |
| `GET` | `/actuator/health/liveness` | Kubernetes liveness check |
| `GET` | `/actuator/health/readiness` | Kubernetes readiness check |

Item responses expose typed fields including `name`, `examine`, `members`,
`lowAlchemy`, `highAlchemy`, `buyLimit`, `value`, `icon`, latest high/low
prices and transaction times, and five-minute average prices and volumes. The
upstream JSON is not stored or returned as an escaped `data` string.

`/api/flips` accepts these query parameters, all optional:

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

For example, the best flips for a 10M budget among active members items:

```bash
curl 'http://localhost:8081/api/flips?budget=10000000&membership=members&minVolume5m=10&size=10'
```

Each result holds the `item` plus `buyPrice`, `sellPrice`, `tax`, `margin`,
`roi`, `quantity`, `potentialProfit`, `volume5m`, `fillableQuantity`,
`estimatedProfit`, `limitedBy` (`buyLimit`, `budget` or `volume`) and
`lastTradeTime`. The quantity and profit fields are `null` for items with no
known buy limit. The page also reports how many matching flips are profitable
or losing, the `topFlip`, and when the most recent trade happened.

Every item is priced and sorted in memory on each request, because the sort
keys depend on the request's price basis and budget. Tax rules and the
exempt-item list live in
[`FlipCalculator.java`](backend/src/main/java/com/flipfinder/service/FlipCalculator.java),
following the OSRS Wiki
[Grand Exchange](https://oldschool.runescape.wiki/w/Grand_Exchange) article.

## Local development

Install the full JDK 25—not only the Java runtime—and make sure both commands
report version 25:

```bash
java -version
javac -version
```

On Ubuntu or Debian-based systems:

```bash
sudo apt update
sudo apt install openjdk-25-jdk
```

Maven itself is not required because the repository includes Maven Wrapper
scripts. The wrapper downloads Maven on its first run, so that first build
needs internet access.

Linux or macOS:

```bash
cd backend
./mvnw clean package
./mvnw spring-boot:run
```

Windows PowerShell or Command Prompt:

```powershell
cd backend
.\mvnw.cmd clean package
.\mvnw.cmd spring-boot:run
```

Local development defaults to port `8081`, leaving the [homelab] Gateway free
to use port `8080`. It also uses the `local` Spring profile and an in-memory
database, so each restart begins with an empty schema and repopulates it:

- API: <http://localhost:8081/api/flips> and <http://localhost:8081/api/items>
- Swagger UI: <http://localhost:8081/swagger-ui.html>

Override the local port when needed:

```bash
SERVER_PORT=9090 ./mvnw spring-boot:run
```

In PowerShell:

```powershell
$env:SERVER_PORT = "9090"
.\mvnw.cmd spring-boot:run
```

## Frontend development

`./mvnw spring-boot:run` serves only the API; the [container](#container)
bundles the web app with it. To work on the web app, install Node.js 24 with
npm, start the backend as above, and in a second terminal run:

```bash
cd frontend
npm install
npm run dev
```

Open <http://localhost:5173>. The Vite dev server reloads on save and forwards
`/api` and Swagger requests to the backend at `http://localhost:8081`. Point
it at another backend, such as the [homelab] deployment, with
`FLIPFINDER_API_URL`:

```bash
FLIPFINDER_API_URL=http://flipfinder.localhost:8080 npm run dev
```

In PowerShell:

```powershell
$env:FLIPFINDER_API_URL = "http://flipfinder.localhost:8080"
npm run dev
```

Run the unit tests and a type-checked production build with:

```bash
npm test
npm run build
```

## Container

Build and run the same image used by Kubernetes. The build compiles the web app
and serves it at <http://localhost:8081> alongside the API:

```bash
docker build --tag flipfinder:local .
docker run --rm --publish 8081:8080 flipfinder:local
```

The container runs as a non-root user. In the default in-memory mode it only
needs a writable `/tmp` for the SQLite native library; persistent mode also
mounts a writable `/data`. It uses the `local` profile by default.

To exercise persistent mode locally with a named Docker volume:

```bash
docker run --rm --publish 8081:8080 \
  --env SPRING_PROFILES_ACTIVE=homelab \
  --env FLIPFINDER_DB_PATH=/data/flipfinder.db \
  --volume flipfinder-data:/data \
  flipfinder:local
```

The environment-specific datasource settings live in:

- [`application-local.properties`](backend/src/main/resources/application-local.properties)
- [`application-homelab.properties`](backend/src/main/resources/application-homelab.properties)

## Releases

Pushing a tag such as `v0.1.0` runs the Java and frontend tests, lints the Helm
chart, and publishes `ghcr.io/griffinseibold/osrs-flip-finder:0.1.0` and
`:latest`:

```bash
git tag v0.1.0
git push origin v0.1.0
```

Update `image.tag` and `appVersion` in the chart when releasing a new version.

## [Homelab] deployment

[`chart/flipfinder`](chart/flipfinder) follows the [homelab] application contract:

- Argo CD can sync the chart directly from this repository.
- The namespace receives `gateway.homelab/access: public`.
- An `HTTPRoute` attaches to `gateway-system/homelab` on its `http` listener.
- The service remains internal on port 80; the container listens on port 8080.
- The `homelab` Spring profile stores SQLite on a 1 Gi `ReadWriteOnce` PVC.
- One replica is used because a persistent SQLite database has one writer.

Argo CD deploys the chart from `master`. Register it once by applying this
Application, which matches the other [homelab] applications:

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: flipfinder
  namespace: argocd
spec:
  project: default
  source:
    repoURL: https://github.com/griffinseibold/Flip-Finder
    path: chart/flipfinder
    targetRevision: master
  destination:
    server: https://kubernetes.default.svc
    namespace: flipfinder
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
```

```bash
kubectl --context kind-homelab-dev apply -f flipfinder-application.yaml
```

Argo CD then keeps the cluster in line with `master`: a merged chart change is
applied automatically. To ship new code, [release](#releases) a new image and
update `image.tag` and `appVersion` on `master`. The default chart values
expose:

- Web app: <http://flipfinder.localhost:8080>
- API: <http://flipfinder.localhost:8080/api/items>
- Swagger UI: <http://flipfinder.localhost:8080/swagger-ui.html>

For a direct Helm deployment rather than Argo CD, build the image and load it
into the Kind cluster, so nothing is pulled from the container registry. Use
one method or the other: while the Argo CD Application exists, it owns these
resources.

```bash
tag=$(git rev-parse --short HEAD)
docker build --tag "flipfinder:$tag" .
kind load docker-image "flipfinder:$tag" --name homelab-dev
kubectl --context kind-homelab-dev create namespace flipfinder \
  --dry-run=client -o yaml | kubectl --context kind-homelab-dev apply -f -
kubectl --context kind-homelab-dev label namespace flipfinder \
  gateway.homelab/access=public --overwrite
helm upgrade --install flipfinder chart/flipfinder \
  --kube-context kind-homelab-dev \
  --namespace flipfinder \
  --set namespace.create=false \
  --set image.repository=flipfinder \
  --set "image.tag=$tag"
```

To update the deployment, commit, then run the same commands again: the new
commit gives the image a new tag, and Helm rolls the pod onto it.

Validate the chart without deploying it:

```bash
helm lint chart/flipfinder
helm template flipfinder chart/flipfinder --namespace flipfinder
```

Set `persistence.enabled=false` to deploy with the in-memory `local` profile
and no PVC.

## RuneScape Wiki API

The website at <https://prices.runescape.wiki/osrs> is the human-facing price
browser. This application uses its documented v2 API at
`https://prices.runescape.wiki/api/v2/osrs`:

- `/mapping` supplies tradeable item metadata.
- `/latest` supplies the most recent high and low prices and Unix transaction
  times.
- `/5m` supplies the current five-minute average prices and traded volumes.

The application makes these three bulk requests once every five minutes. It
does not loop over item IDs. At verification time `/mapping` contained 4,662
items, while `/latest` contained 4,539 items; the difference is expected
because items that have never been observed trading are omitted from
`/latest`. Five-minute coverage varies with activity in each interval.

The [official real-time price API
documentation](https://oldschool.runescape.wiki/w/RuneScape%3AReal-time_Prices)
lists these important constraints:

- There is no fixed published rate limit, but sustained multiple large
  requests per second can be blocked. Bulk endpoints should be used instead of
  one request per item.
- A descriptive `User-Agent` is required. Generic Java, curl, Apache HTTP
  Client, and similar default agents may be pre-emptively blocked.
- `/latest` can omit never-traded items, and either side of an item's price can
  be `null` if that transaction type has not been observed.
- Prices can exceed 32-bit integer range, and average prices can be decimal.
  The database therefore uses 64-bit integer-compatible columns for prices and
  volumes and floating-point columns for averages.

For a real-time production pipeline, the API maintainers also ask users to
join the RuneScape Wiki Discord `#api-discussion` channel for maintenance and
breaking-change announcements.

[homelab]: https://github.com/griffinseibold/Homelab
