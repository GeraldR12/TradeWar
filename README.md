# TradeWar

![Java](https://img.shields.io/badge/Java-21-orange)
![Paper](https://img.shields.io/badge/Paper-1.21.x-blue)
![Build](https://img.shields.io/badge/build-Maven-red)

**TradeWar** is a [Paper](https://papermc.io/) plugin for the CartMC server that adds an economic-diplomacy layer on top of [Towny](https://github.com/TownyAdvanced/Towny) and [QuickShop-Hikari](https://ghostchu.github.io/quickshop-hikari-wiki/): towns can impose trade tariffs on one another, sanction rival towns out of their markets, and nations can declare full trade embargoes.

## Features

- **Tariffs** — a town can impose an import or export tariff on another town, targeting a specific item or all goods, for a fixed duration or permanently. The tariff is applied as a surcharge on matching QuickShop-Hikari purchases and deposited straight into the issuing town's bank.
- **Sanctions** — a town can block another town's residents from trading in its shops entirely.
- **Embargoes** — a nation can cut off all trade with a rival nation.
- **Live feedback** — every policy change is broadcast server-wide with a sound cue, and optionally mirrored to a Discord channel via webhook.
- **BlueMap layer (optional)** — when [BlueMap](https://bluemap.bluecolored.de/) is installed, TradeWar adds its own `Trade Wars` marker layer showing which towns are currently tariffed, sanctioned, or embargoed, and by whom. Fully optional: TradeWar works exactly the same without BlueMap installed.

## Requirements

| Dependency | Version |
|---|---|
| Java | 21 |
| Paper | 1.21.x |
| Towny | 0.102.0.13+ |
| QuickShop-Hikari | 6.2.0.11+ |
| Vault | any recent build (economy provider) |
| BlueMap | optional, tested against `5.16-paper` (BlueMapAPI 2.7.7) |

## Commands

```
/tw tariff add <import|export> <target_town> <item|all> <percent> [minutes]
/tw tariff remove
/tw tariff list

/tw sanction add <target_town>
/tw sanction remove <target_town>
/tw sanction list

/tw embargo add <target_nation>
/tw embargo remove <target_nation>
/tw embargo list
```

Alias: `/tradewar`

Tariff/sanction management requires being the town Mayor; embargo management requires being the nation King. Either can be bypassed with the `tradewar.admin` permission.

## Permissions

| Node | Description | Default |
|---|---|---|
| `tradewar.admin` | Bypass Mayor/King checks when managing tariffs, sanctions, or embargoes | `op` |

## Configuration

`config.yml`:

```yaml
discord-webhook-url: "YOUR_WEBHOOK_URL_HERE"

tariffs:
  max-percentage: 100.0
```

- `discord-webhook-url` — Discord webhook to receive trade-event notifications. Leave as the placeholder to disable.
- `tariffs.max-percentage` — hard ceiling on any single tariff a town can set.

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

### BlueMap integration

If [BlueMap](https://bluemap.bluecolored.de/) is present (`softdepend`, never required), TradeWar registers its own `Trade Wars` `MarkerSet` and places one marker per town that currently has an active restriction — towns with none get no marker at all. Sanctions/embargoes target names are resolved against Towny case-insensitively; a policy referencing a town/nation that no longer exists is skipped and logged (`[BlueMap] ... not found (skipped)`), never shown as a marker. Nation embargoes are expanded to every member town at read time — the stored policy stays `Nation -> Nation`, nothing extra is persisted.

Clicking a town's marker shows every restriction affecting it in one popup, worst severity first color-wise (`Embargo > Sanction > Tariff`):

```
Havana
Restrictions: 3

⛔ NATION EMBARGO
Applied by: UnitedKingdom

🔴 SANCTION
Applied by: Brasilia

🟠 IMPORT TARIFF
Applied by: NewYork
15%
IRON_INGOT
Expires: 2h 14m
```

Markers update on `/tw tariff add`, `/tw sanction add/remove`, and `/tw embargo add/remove` without a restart (only the affected town(s) are touched); `/tw tariff remove` and BlueMap/TradeWar (re)load trigger a full rebuild. Expired tariffs disappear on their own — a single self-rescheduling timer (no polling) sweeps the map exactly when the next tariff is due to expire. TradeWar's territory marker is placed at the town's spawn point (`Town#getSpawnOrNull()`); it does not draw territory outlines and does not touch BlueMap-Towny's own markers — the two coexist as independent `MarkerSet`s.

If BlueMap isn't installed, TradeWar logs one line (`BlueMap not found - map integration disabled.`) and runs exactly as before.

## Building from source

```bash
mvn clean package
```

Produces a shaded, drop-in jar at `target/TradeWar-<version>.jar`.

## Project status

Tariff command input is hardened against invalid targets, materials, percentages, and durations. QuickShop tax accounting reconciliation and persistence hardening are tracked as ongoing work, not yet part of this release. The optional BlueMap layer is implemented and unit-tested locally; in-game/BlueMap-rendered verification on a live server is still pending.
