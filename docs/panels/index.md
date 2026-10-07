---
title: Panels
order: 1
description: A tour of every Lens panel.
icon: lucide:layout-panel-left
---

# Panels

Each panel comes from a collector. Collectors never throw into your request. If one fails, Lens logs the error to the `bxLens` logger and skips that panel for the request.

| Panel | On by default | What it shows |
|---|---|---|
| [Timeline](timeline.md) | yes | One waterfall for templates, functions, queries and HTTP calls. |
| [Queries](queries.md) | yes | SQL, timing, params, duplicates and N+1. |
| [Templates](templates.md) | yes | The include and call tree. |
| [HTTP](http.md) | yes | Outgoing HTTP calls. |
| [Exceptions](exceptions.md) | yes | Thrown and caught exceptions. |
| [Messages](messages-and-timers.md#messages) | yes | Output from `lensMessage` and `lensDump`. |
| [Timers](messages-and-timers.md#timers) | yes | Output from `lensStart`, `lensStop` and `lensMeasure`. |
| [Request and Scopes](request-and-scopes.md) | yes | Request details and scope contents. |
| [JVM](jvm.md) | yes | Runtime and memory information. |
| [Cache](cache.md) | no | Per-request cache stats. |
| [Modules](modules.md) | yes | Loaded modules. |
| [History](history.md) | yes | The last 50 requests. |
| [Issues](issues.md) | yes | Everything suspicious, ranked. |

Opt-in collectors: `functions`, `cache`, `logs` and `session`. Enable them under `collectors` in [Configuration](../configuration.md#collectors).

## Tab badges

Each tab can show a count with a severity of none, warn or crit. Badges feed the health strip and the Issues tab.

## Add your own

Modules and app code can add panels. See [Extending Lens](../guides/extending.md).
