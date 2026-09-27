# OSRS Flip Finder

Flip Finder ranks Old School RuneScape Grand Exchange items by how much you
could make flipping them after tax, using live prices from the RuneScape Wiki.
Set a budget and it shows the best flips you can afford, how fast each one is
trading, and what one four-hour buy limit of it could earn.

## Run it on your computer

You need [Docker](https://docs.docker.com/get-started/get-docker/).

```bash
docker run --rm --publish 8081:8080 ghcr.io/griffinseibold/osrs-flip-finder:latest
```

Open <http://localhost:8081>. Prices load a few seconds after it starts and
refresh every five minutes. Press Ctrl+C to stop it.

## Run it on the homelab

You need a running [homelab]. Register Flip Finder with its Argo CD once:

```bash
kubectl --context kind-homelab-dev apply -f https://raw.githubusercontent.com/griffinseibold/Flip-Finder/master/deploy/argocd-application.yaml
```

Open <http://flipfinder.localhost:8080>. Argo CD keeps it in step with this
repository from then on. The homelab version also keeps its price database
between restarts, and will gain features that rely on the homelab.

## Learn more

- [Using Flip Finder](docs/using-flip-finder.md): what each column means and
  how flips are ranked.
- [API](docs/api.md): the HTTP endpoints behind the web app.
- [Development](docs/development.md): building, testing and running from
  source, with and without the homelab, and how releases work.
- [Homelab deployment](docs/homelab.md): the Helm chart, Argo CD and how
  updates reach the homelab.
- [RuneLite plugin](docs/runelite-plugin.md): sends your membership, coins and
  buy limits to the homelab version, so its flips fit your account.
- [Price data](docs/price-data.md): where prices, trading volumes, buy limits
  and tax rules come from.

[homelab]: https://github.com/griffinseibold/Homelab
