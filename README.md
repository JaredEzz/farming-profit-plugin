# Farming Profit

A [RuneLite](https://runelite.net) plugin that tracks the crops you harvest during a farm
run and calculates the profit, using live Grand Exchange prices for the seeds and the
products.

A side panel lists each patch you harvest with its product image, amount and profit, plus a
running total for the whole run. Hover a patch for a cost/products/profit breakdown.

### Supported patch types

* Herbs
* Allotments
* Hops
* Bushes
* Special (cactus + seaweed)

Each patch type can be toggled on or off in the plugin configuration.

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