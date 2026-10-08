---
title: Console settings
order: 6
description: The read-only Settings page and what it shows.
icon: lucide:settings
---

# Settings

The Settings page shows the values Lens is running with. It is read-only. To change a value, edit `boxlang.json` and restart the runtime or reload the module. See [Configuration](../configuration.md) for every key.

![The Settings page](../assets/screenshots/console-settings.png)

## Access and collection

| Value | Shown as |
|---|---|
| `console.enabled`, `console.access`, `console.sessionMinutes` | The effective value. |
| `console.password` | Only `set` or `not set`. The password is never sent to the browser. |
| `bar.enabled`, `bar.access`, `bar.allowAllIPs` | The effective value. |
| `collect.level` | `off`, `light` or `full`. |
| `history.maxRequests` | Size of the request buffer. |

## Collectors

Every registered collector, marked light or heavy. Heavy collectors (`functions`, `logs`, `scopes`) are skipped at `collect.level: "light"`. A collector that is turned off does not appear.

## Hidden tabs

The ids listed in `tabs.hide`.

## License

The header of every console page shows the license state (BoxLang+ active, Trial, License expired or Free). See [Licensing](../licensing.md).
