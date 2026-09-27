# RuneLite plugin

The Flip Finder plugin for [RuneLite](https://runelite.net) sends your
account's details to the homelab version of Flip Finder, so it can tailor flips
to you instead of asking for a budget and filters:

- whether the account is a member, and how many membership days are left
- whether it is an ironman, which cannot use the Grand Exchange
- the coins and platinum tokens in the inventory and, once you open it, the
  bank
- your Grand Exchange offers
- how many of each item you have bought in its current four-hour buy limit
  window

It sends nothing until you turn it on, and then only to the server you set.
Receiving the data needs the homelab; the standalone Docker version does not
accept it.

## Running it

The plugin is not on the Plugin Hub, so run RuneLite with it from this
repository. You need JDK 17 or newer:

```bash
cd runelite-plugin
./gradlew run
```

RuneLite opens in developer mode with the plugin loaded. To log in with a
Jagex account, follow RuneLite's
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
instructions.

In RuneLite's configuration, open **Flip Finder**:

1. Check **Server URL**. The default, `http://flipfinder.localhost:8080`, is
   the homelab.
2. Turn on **Send account data** and accept RuneLite's warning.
3. Open your bank once so the plugin can see the coins in it.

The plugin sends an update within ten seconds of a change, and every five
minutes otherwise.

## Checking it works

List the accounts the server has heard from:

```bash
curl http://flipfinder.localhost:8080/api/runelite/accounts
```

Each has an `accountHash`. Pass it to the flips API to rank flips with the
account's coins as the budget, its membership, and what is left of each buy
limit:

```bash
curl 'http://flipfinder.localhost:8080/api/flips?account=<accountHash>&size=5'
```

The [API](api.md#runelite-accounts) page describes both responses.

## What it can and cannot see

- **Bank coins** are only readable while the bank is open, so the plugin
  remembers the last amount it saw. Until you have opened the bank with the
  plugin running, the account has no known total and no budget is applied.
- **Buy limits** count purchases the plugin sees. Offers that fill while you
  are logged out are counted when you next log in, as if bought then. The
  first time the plugin runs, fills of offers already in your slots count from
  that login. Purchases made on another device while the plugin is not running
  are missed if the offer is collected there.
- Some items share one limit, such as the doses of a potion. The plugin counts
  each item separately.

## Privacy

The plugin sends your account's RuneLite hash and display name with the
details above, over plain HTTP, to the server you configure. The homelab
receives it on the host's loopback address only, and the endpoint has no
authentication, so do not expose it beyond your own machine.

## Development

The plugin follows RuneLite's
[example plugin](https://github.com/runelite/example-plugin) and targets Java
11. `./gradlew build` compiles it and runs its tests. The code is in
[`runelite-plugin/`](../runelite-plugin), and the server side in
[`RuneLiteController`](../backend/src/main/java/com/flipfinder/controller/RuneLiteController.java).
