# TradeWar

![Java](https://img.shields.io/badge/Java-21-orange)
![Paper](https://img.shields.io/badge/Paper-1.21.x-blue)
![Build](https://img.shields.io/badge/build-Maven-red)

**TradeWar** is a [Paper](https://papermc.io/) plugin for the CartMC server that adds an economic-diplomacy layer on top of [Towny](https://github.com/TownyAdvanced/Towny) and [QuickShop-Hikari](https://ghostchu.github.io/quickshop-hikari-wiki/): towns can impose trade tariffs on one another, sanction rival towns out of their markets, and nations can declare full trade embargoes.

## Features

All trade policy is managed through Towny's own `/town` (`/t`) and `/nation` (`/n`) commands. TradeWar adds `tariff`, `sanction` and `embargo` subcommands to both.

- **Who sets policy** — a **nation** sets policy for all its towns, through its capital (`/n ...`). A **town without a nation** sets its own (`/t ...`). A town that belongs to a nation has no trade policy of its own.
- **Tariffs** — a surcharge on all goods your shops sell to a target nation or town, for a fixed duration or permanently. The buyer pays it on top of the shop price, and it goes straight into the bank of the nation or town that set it.
- **Sanctions** — block a specific town or player from trading with your shops. Your own people also can't buy from theirs.
- **Embargoes** — cut off all trade, in both directions, with every town of a nation.
- **Live feedback** — every policy change is broadcast server-wide with a sound cue, and optionally mirrored to a Discord channel via webhook.
- **BlueMap layer (optional)** — when [BlueMap](https://bluemap.bluecolored.de/) is installed, TradeWar adds its own `Trade Wars` marker layer showing which towns are currently tariffed, sanctioned, or embargoed, and by whom. Fully optional: TradeWar works exactly the same without BlueMap installed.
- **Pl3xMap layer (optional)** — the same `Trade Wars` layer for [Pl3xMap](https://modrinth.com/plugin/pl3xmap), with the same popups. Also fully optional, and it can run alongside BlueMap.

## Requirements

| Dependency | Version |
|---|---|
| Java | 21 |
| Paper | 1.21.x |
| Towny | 0.102.0.13+ |
| QuickShop-Hikari | 6.2.0.11+ |
| Vault | any recent build (economy provider) |
| BlueMap | optional, tested against `5.16-paper` (BlueMapAPI 2.7.7) |
| Pl3xMap | optional, built against `1.21.11-544` |

## Commands

The same commands exist under `/t` (towns without a nation) and `/n` (nations):

```
/n tariff add <nation:Name|town:Name> <percent> [duration]
/n tariff remove <nation:Name|town:Name|all>
/n tariff list

/n sanction add <town:Name|player:Name>
/n sanction remove <town:Name|player:Name>
/n sanction list

/n embargo add <nation>
/n embargo remove <nation>
/n embargo list
```

Replace `/n` with `/t` for a town without a nation. Everything tab-completes.

- **Tariff targets** — `nation:Name` covers every town of that nation, and `town:Name` covers one town. A bare name is tried as a nation first, then as a town. Each target can have at most one tariff; adding one again replaces it. Tariffs on the same buyer stack: for example, a nation tariff plus a tariff on the buyer's own town.
- **Duration** — `s`/`m`/`h`/`d` suffixes (`30m`, `2h`, `1d`). A bare number means minutes. Leave it out for a permanent tariff.
- **Sanction targets** — `town:Name` or `player:Name`. A bare name is tried as a town first. Player sanctions are stored by UUID, so a player renaming doesn't lift them.
- **Shop prices** — clicking a shop that tariffs you shows the price per item including the tariff. A purchase you can't afford with the tariff added is refused.
- **Who can use them:**

| Scope | Can add / remove | Can `list` |
|---|---|---|
| `/n` | The King (the capital's mayor), or a resident **of the capital** whose Towny nation rank has `towny.command.nation.<tariff\|sanction\|embargo>` | Any citizen of the nation |
| `/t` (town not in a nation) | The Mayor, or a resident whose Towny town rank has `towny.command.town.<tariff\|sanction\|embargo>` | Any resident of the town |
| `/t` (town in a nation) | Nobody: the player is pointed to `/n` | Pointed to `/n` |

Rank nodes are granted in Towny's `townyperms.yml`.

### On the `/town` and `/nation` screens

`/town [name]` and `/nation [name]` show `[Trade Tariffs]`, `[Trade Sanctions]` and `[Trade Embargoes]` entries next to Towny's own. Each one only appears if there's something in it. Hovering an entry lists its targets, the same way hovering `[Residents]` lists residents. Tariffs show their rate and time left.

A town in a nation shows its nation's policies, labeled "Set by the Nation of ...", because those are the ones that apply to its shops. Towny's built-in `[Sanctioned Towns]` entry on nations is a separate Towny feature (`/n sanctiontown`), not TradeWar's.

### Reloading

```
/ta reload tradewar
```

This re-reads `config.yml` and `data.yml` and redraws the map layers without a restart. It uses Towny's own reload permission, `towny.command.townyadmin.reload`.
- **Applies right away:** `bluemap/pl3xmap.show-*`, `tariffs.max-percentage` and `discord-webhook-url`.
- **Needs a restart:** `bluemap.enabled`/`pl3xmap.enabled` and the layer labels.

**Towny changes are followed automatically:**
- **Joining a nation:** when a town joins a nation, its own policies are **paused**, and its online residents are told. Policies the town set while independent stay saved.
- **Leaving a nation:** when the town leaves, its paused policies apply again.
- **Renames:** renaming a town or nation carries its policies, and every policy that targets it, over to the new name.
- **Deletions:** deleting a town or nation drops the policies it had set.

## Permissions

| Node | Description | Default |
|---|---|---|
| `tradewar.admin` | Manage your own town's/nation's policies without being Mayor/King or holding a rank node. It can't let a town that is in a nation act on its own. | `op` |

## Upgrading from 1.3.x (`/tw`)

`/tw` and `/tradewar` are gone. On first start, an old `data.yml` is migrated automatically, and the original is kept as `data.yml.pre-towny-commands`:

- **Embargoes** become nation embargoes, unchanged.
- **Town sanctions** become town sanctions (`town:Name`).
- **Town tariffs on "all goods"** become flat town tariffs. If a town had both an import and an export tariff on the same town, the higher rate is kept.
- **Item-specific tariffs are dropped.** Tariffs now always cover all goods. Each dropped tariff is logged as `[Migration] Dropped item-specific tariff ...`.
- **Towns now in a nation:** policies set by a town that is in a nation are kept but paused, since only the nation's policies apply.

**QuickShop tax setting:** tariffs are now charged by TradeWar itself. If QuickShop's `shop-tax.type` was set to `tradewar_tariffs`, TradeWar keeps a zero-rate provider under that name so purchases still work, and logs a warning. Set it back to `basic` if you want QuickShop's own tax back.

## Configuration

`config.yml`:

```yaml
discord-webhook-url: "YOUR_WEBHOOK_URL_HERE"

tariffs:
  max-percentage: 100.0
```

- `discord-webhook-url` — Discord webhook to receive trade-event notifications. Leave as the placeholder to disable.
- `tariffs.max-percentage` — hard ceiling on any single tariff a nation or town can set.

```yaml
bluemap:
  enabled: true
  marker-set-label: "Trade Wars"
  show-tariffs: true
  show-sanctions: true
  show-embargoes: true
```

- `bluemap.enabled` — master switch for the BlueMap layer. No effect if BlueMap isn't installed either way.
- `bluemap.marker-set-label` — display name of the marker group/layer toggle shown in the BlueMap UI.
- `bluemap.show-tariffs` / `show-sanctions` / `show-embargoes` — hide a restriction type from the map without disabling the policy itself.

```yaml
pl3xmap:
  enabled: true
  layer-label: "Trade Wars"
  show-tariffs: true
  show-sanctions: true
  show-embargoes: true
```

- `pl3xmap.enabled` — master switch for the Pl3xMap layer. No effect if Pl3xMap isn't installed either way.
- `pl3xmap.layer-label` — display name of the layer toggle shown in the Pl3xMap UI.
- `pl3xmap.show-tariffs` / `show-sanctions` / `show-embargoes` — same as the BlueMap options, configured separately so each map can show different restriction types.

### BlueMap integration

If [BlueMap](https://bluemap.bluecolored.de/) is present (`softdepend`, never required), TradeWar registers its own `Trade Wars` `MarkerSet`. It places one marker per town that currently has an active restriction; towns with none get no marker.

- **Name matching:** target names are matched against Towny without regard to case. A policy that refers to a town or nation that no longer exists is skipped and logged (`[BlueMap] ... not found (skipped)`), and never shown as a marker.
- **Nation targets:** an embargo or tariff on a nation shows on every town of that nation.
- **What isn't shown:** player sanctions aren't drawn, and neither are the paused policies of towns that are in a nation.
- **Who applied it:** a nation shows as `Name (nation)` under "Applied by", and a town shows as its plain name.

Clicking a town's marker shows every restriction affecting it in one popup. The marker is colored by the worst one (`Embargo > Sanction > Tariff`):

```
Havana
Restrictions: 3

⛔ EMBARGO
Applied by: UnitedKingdom (nation)

🔴 SANCTION
Applied by: Freeport

🟠 TARIFF
Applied by: Brasil (nation)
15% on all goods
Expires: 2h 14m
```

Markers update without a restart:
- **Adding or removing a policy** updates only the affected town(s).
- **A full rebuild** happens on `tariff remove all`, when a town joins or leaves a nation, on a rename or deletion, and when BlueMap or TradeWar (re)loads. Expired tariffs disappear on their own — a single self-rescheduling timer (no polling) sweeps the map exactly when the next tariff is due to expire. TradeWar's territory marker is placed at the town's spawn point (`Town#getSpawnOrNull()`); it does not draw territory outlines and does not touch BlueMap-Towny's own markers — the two coexist as independent `MarkerSet`s.

If BlueMap isn't installed, TradeWar logs one line (`BlueMap not found - map integration disabled.`) and runs exactly as before.

### Pl3xMap integration

If [Pl3xMap](https://modrinth.com/plugin/pl3xmap) is present (`softdepend`, never required), TradeWar registers its own `Trade Wars` layer (a toggleable `SimpleLayer`) in every Pl3xMap world. It works the same way as the BlueMap integration: it uses the same restriction data, the same popup content, and the same update triggers, including the expiry timer.

Each restricted town gets a circle marker with a 24-block radius, centered on the town spawn and colored by its worst restriction:

| Severity | Color |
|---|---|
| Embargo | dark red |
| Sanction | red |
| Tariff | orange |

Hovering over a marker shows the town name; clicking it opens the same popup as on BlueMap (see above). If Pl3xMap reloads a world, the layer is rebuilt automatically. Towns whose spawn is in a world Pl3xMap doesn't render are skipped and logged (`[Pl3xMap] World not rendered by Pl3xMap, marker skipped for town: ...`).

BlueMap and Pl3xMap can both be installed at once; each gets its own layer and they don't affect each other. If Pl3xMap isn't installed, TradeWar logs `Pl3xMap not found - map integration disabled.` and runs exactly as before.

## Building from source

```bash
mvn clean package
```

Produces a shaded, drop-in jar at `target/TradeWar-<version>.jar`.

## Project status

Policy commands run as Towny `/t` and `/n` subcommands. Tariffs are collected from the buyer directly, so no money is created and QuickShop's tax isn't involved. The policy rules, the old-data migration and the rename handling are unit-tested. The Towny commands and the QuickShop charging have not yet been verified on a live server. The optional BlueMap layer is implemented and unit-tested locally; in-game/BlueMap-rendered verification on a live server is still pending. The optional Pl3xMap layer shares the same tested aggregation and formatting code; the Pl3xMap-specific layer and marker code has no tests of its own.
