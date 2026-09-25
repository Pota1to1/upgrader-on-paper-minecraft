# Upgrader (Paper plugin port)

A from-scratch Paper plugin that replicates the "Upgrader" mod: right-click
an Upgrader item to open a GUI, drop in any item, pick a target item from a
paginated catalog, and gamble on turning it into the target. Odds are
**not hand-tuned** — every item's value is derived live from Bukkit's own
registered recipes (crafting, smelting/blasting/smoking/campfire,
stonecutting, smithing), walked back to a small table of raw-material base
values, exactly like the original mod's approach. Success chance is
`0.9 * (yourValue / targetValue)`, clamped between 0.1% and 90%, and the
outcome is rolled entirely server-side, so it's safe for you and your friends
to use on a shared server.

## What you get
- `com.upgrader.UpgraderPlugin` — main class, registers a crafting recipe
  (4 gold ingots + 4 diamonds + an anvil) for the Upgrader item, plus an
  `/upgrader` command (op only by default) as a shortcut to get one.
- `com.upgrader.ItemValueService` — the recipe-walking pricer.
- `com.upgrader.UpgraderGUI` — the wheel/gamble GUI (input slot, paginated
  target catalog, live odds display, spin animation).
- `com.upgrader.UpgraderListener` — wires right-click and GUI events together.

## Build it
You'll need internet access and JDK 21+ (this environment doesn't have
network access, so I couldn't compile it here — but the source is complete
and ready to build on your own machine):

```bash
cd upgrader-plugin
mvn package
```

The jar will land in `target/UpgraderPlugin.jar`.

`pom.xml` is pinned to Paper `26.3-R0.1-SNAPSHOT` and `plugin.yml`'s
`api-version` is set to `26.3` to match. **If the build fails with a
"could not resolve dependency" error:** Paper's exact Maven version string
moves with every release, so open
https://repo.papermc.io/#browse/browse:maven-public:io%2Fpapermc%2Fpaper%2Fpaper-api
and copy whatever the newest folder name is into `<paper.version>`, then
rebuild.

## Install it
1. Stop your server.
2. Drop `UpgraderPlugin.jar` into your server's `plugins/` folder.
3. Start the server, then run `plugins` in the console — you should see
   `Upgrader` listed as enabled (check `logs/latest.log` if not, for a
   version-mismatch or dependency error).
4. Craft one (4 gold ingots + 4 diamonds around an anvil, standard 3x3 grid)
   or run `/upgrader` as an op to get one directly.

## How it plays
- Right-click the Upgrader item to open the GUI.
- Put the item you're gambling into the top slot.
- Click a target item in the catalog grid (use the arrows to page through it).
- The book icon shows the live success chance once both are set.
- Hit **SPIN**. There's a short animated flicker, then either your item
  becomes the target (success) or it's gone for good (fail) — the result was
  actually decided the instant you clicked, the flicker is just visual flair.
- Closing the GUI at any other time just gives your input item back.

## Tuning
- `ItemValueService.BASE_VALUES` is the seed table for raw materials with no
  recipe of their own (ores, logs, diamonds, etc.) — tweak these to rebalance
  everything downstream, since all crafted items derive their price from them.
- `ItemValueService.isValuable()` controls which materials are excluded from
  the target catalog (potions, enchanted books, spawn eggs, etc. — items
  whose value lives in NBT rather than the base item, same exclusion the
  original mod makes).
- The catalog only rebuilds once per server run (first GUI open) since
  scanning `Material.values()` and pricing all of them is a touch of work;
  call `ItemValueService#clearCache()` and restart if you change base values
  and want an instant re-price during testing.
