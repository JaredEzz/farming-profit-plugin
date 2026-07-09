# Farming Profit

A [RuneLite](https://runelite.net) plugin for farm runs. The side panel has two tabs:

### Tracker

Lists each patch you harvest with its product image, amount and value, plus a running total
for the run. Hover a patch for a cost / products / profit / XP breakdown.

* **Mains** see Grand Exchange profit (live GE prices for the seeds and herbs).
* **Ironmen** see Farming **XP** instead — since they can't sell on the GE, profit is
  meaningless, so the tracker shows the experience each patch gave.

The account type is detected automatically (`client.getAccountType()`), and you can override
it from *Value patches by* in the config (Auto / Profit / XP).

Supported patch types (each toggleable in config): Herbs, Allotments, Hops, Bushes, Special
(cactus + seaweed).

### Herb planner

Ranks every herb you can currently plant by **profit per run** (mains) or **XP per run**
(ironmen), so you always know the best herb to grow. It reimplements the OSRS wiki's herb
yield model and pulls in your real situation:

* Your **Farming level** (read live).
* **Magic secateurs** (+10%) — detected whether equipped or in your inventory.
* **Farming / Max cape** (+5%, herbs only) — detected when worn.
* **Compost** tier, **number of patches**, and an optional **Attas** plant (+5%) — from config.

Expected yield per patch is `lives / (1 - chanceToSave)`, where the chance-to-save constants
are boosted by your gear before the level interpolation — matching
`Module:Herb_Farming_calculator`. Profit uses the live grimy-herb GE price minus the seed cost.

> Not modelled: per-patch diary yield bonuses (e.g. Kandarin at Catherby) — these don't change
> the herb ranking and vary by patch. Magic secateurs / Farming cape are untradeable, so they're
> detected, never priced.

### Notifications

Optionally pushes a phone notification via [ntfy](https://ntfy.sh) when your whole herb run is
ready — including while you're logged out, as long as RuneLite itself is still open.

Off by default. Enable it in **Farming Profit config → Notifications → Push herb-run
notifications**, then set **ntfy topic URL** to a topic on the public `ntfy.sh` server (e.g.
`https://ntfy.sh/<something-long-and-random>`) or a self-hosted ntfy instance — any
ntfy-compatible URL works, the plugin doesn't require its own server.

* `ntfy.sh` topics are unauthenticated by name — the topic name *is* the only privacy boundary,
  so pick something long and unguessable rather than your username or "herbs". Anyone who knows
  the exact topic can subscribe or publish to it.
* For an access-controlled topic, append a Bearer token after a pipe:
  `https://ntfy.sh/my-topic|tk_xxxxxxxxxx`.
* Sends only a short status message (a patch count, no account info) over HTTPS.
* **Notify per patch** switches from one push for the whole run to one per individual patch as
  it finishes.

### Debug log

If something looks wrong (a patch not tracking correctly, a stat that seems off), turn on
**Farming Profit config → Debug → Enable debug logging**, reproduce the issue, then right-click
the Tracker tab's totals box and choose **Copy debug log** to copy the plugin's recent internal
log (patch state changes, harvest events, any errors) to your clipboard — handy for pasting into
a bug report when you don't have a Java console handy (e.g. on Steam Deck). Nothing is sent
anywhere; it only fills an in-memory buffer you copy yourself. Off by default.

## Attribution

This plugin is the externalised version of the original
[RuneLite PR #6585](https://github.com/runelite/runelite/pull/6585) by **Mika Kuijpers
(@mkuijpers)**, which was closed with the `should-be-external` label so it could live on the
Plugin Hub instead of in the core client. The original BSD 2-Clause license and authorship are
preserved.

Changes made while externalising:

* Restructured into the standalone Plugin Hub project layout (`com.farmingprofit`).
* `net.runelite.client.util.StackFormatter` → `QuantityFormatter` (class was renamed upstream).
* `com.google.common.eventbus.Subscribe` → `net.runelite.client.eventbus.Subscribe` (RuneLite
  moved off Guava's EventBus; with the old import the event handlers silently never fired).
* Removed the leftover debug overlay that displayed internal tracking IDs.

Features added on top of the original:

* Account-type detection with profit-for-mains / XP-for-ironmen display (and a manual override).
* A second "Planner" tab that ranks herbs by profit or XP per run using your live level and gear.
* Per-crop Farming level / planting XP / harvest XP and per-herb chance-to-save data, with unit
  tests covering the yield model against the wiki's reference values.

> Note: the crop table still uses the (now deprecated but fully functional)
> `net.runelite.api.ItemID` / `AnimationID` / `SpriteID` / `InventoryID` constants. A future
> cleanup could migrate these to the `net.runelite.api.gameval.*` equivalents.

## Building

Requires a JDK 11 (RuneLite's build target).

```sh
./gradlew build
```

The plugin compiles against `net.runelite:client:latest.release` pulled from
`https://repo.runelite.net`.

## Running / testing locally

The `run` task launches a full RuneLite client in developer mode with this plugin loaded as a
built-in, so you can test it against the live game:

```sh
./gradlew run
```

This uses `com.farmingprofit.FarmingProfitPluginTest` as the entry point
(`ExternalPluginManager.loadBuiltin(...)` + `RuneLite.main(...)`). Log in, do a farm run, and
open the **Farming Profit** side panel.

On CachyOS just make sure a JDK 11 is on `PATH` (e.g. `sudo pacman -S jdk11-openjdk` and
`archlinux-java set java-11-openjdk`, or point `JAVA_HOME` at any JDK 11) before running the
Gradle wrapper.

## Submitting to the Plugin Hub

1. Push this repository to a public Git host (e.g. GitHub) and note the commit hash you want to
   publish.
2. Fork [`runelite/plugin-hub`](https://github.com/runelite/plugin-hub).
3. Add a file `plugins/farming-profit` (no extension) containing:

   ```properties
   repository=https://github.com/<you>/farming-profit-plugin.git
   commit=<full 40-char commit hash>
   ```

4. Open a PR against `runelite/plugin-hub`. CI builds the plugin from that exact commit and a
   maintainer reviews it.

See the [Plugin Hub README](https://github.com/runelite/plugin-hub) for the authoritative,
up-to-date submission rules.