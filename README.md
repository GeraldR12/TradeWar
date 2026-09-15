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

## Requirements

| Dependency | Version |
|---|---|
| Java | 21 |
| Paper | 1.21.x |
| Towny | 0.102.0.13+ |
| QuickShop-Hikari | 6.2.0.11+ |
| Vault | any recent build (economy provider) |

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

## Building from source

```bash
mvn clean package
```

Produces a shaded, drop-in jar at `target/TradeWar-<version>.jar`.

## Project status

Tariff command input is hardened against invalid targets, materials, percentages, and durations. QuickShop tax accounting reconciliation and persistence hardening are tracked as ongoing work, not yet part of this release.
