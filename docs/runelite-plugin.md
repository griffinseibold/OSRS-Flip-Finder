# RuneLite plugin

The homelab version of Flip Finder learns about your account from the
[Flip Finder Agent](https://github.com/griffinseibold/flip-finder-plugin)
RuneLite plugin:
whether you are a member, how many coins you have, your Grand Exchange offers,
and how much of each item's four-hour buy limit you have used. With that, it
suggests flips you can actually make instead of asking for a budget and
filters. The standalone Docker version does not use the plugin.

## Setting it up

You need [RuneLite](https://runelite.net) and Flip Finder running on the
[homelab](homelab.md).

1. In RuneLite, open **Configuration**, then **Plugin Hub**, search for
   **Flip Finder Agent** and select **Install**.
2. Open the plugin's settings. The default **Server URL**,
   `http://flipfinder.localhost:8080`, is the homelab.
3. Turn on **Send account data** and accept RuneLite's warning.
4. Log in and open your bank once, so the plugin can see the coins in it.

Flip Finder Agent is waiting for review before it appears on the Plugin Hub
([runelite/plugin-hub#17204](https://github.com/runelite/plugin-hub/pull/17204)).
Until then, its
[README](https://github.com/griffinseibold/flip-finder-plugin#development)
explains how to run it from source, and what it can and cannot see.

## Checking it works

List the accounts Flip Finder has heard from:

```bash
curl http://flipfinder.localhost:8080/api/runelite/accounts
```

Each has an `accountHash`. The flips API takes it to rank flips with the
account's coins as the budget, its membership, and what is left of each buy
limit:

```bash
curl 'http://flipfinder.localhost:8080/api/flips?account=<accountHash>&size=5'
```

The [API](api.md#runelite-accounts) page describes both responses.

## Privacy

The plugin sends your account's RuneLite hash and display name with the
details above, over HTTP, to the server you set. The homelab receives it on
the host's loopback address only, and the endpoint has no authentication, so
do not expose it beyond your own machine.
