---
title: Panels
order: 1
description: A tour of every Lens panel.
icon: lucide:layout-panel-left
---

# Panels

Each panel comes from one or more collectors. Collectors never throw into your request. If one fails, Lens logs the error to the `bxLens` logger and skips that panel for the request.

| Panel | On by default | What it shows |
|---|---|---|
| [Issues](issues.md) | yes | Everything suspicious, ranked. |
| [Timeline](timeline.md) | yes | One waterfall for templates, functions, queries, HTTP calls, transactions and timers. |
| [Queries](queries.md) | yes | SQL, timing, params, duplicates and N+1. |
| [Templates](templates.md) | yes | The include and call tree. Functions need an opt-in. |
| [HTTP](http.md) | yes | Outgoing HTTP calls. |
| [Exceptions](exceptions.md) | yes | Thrown and caught exceptions. |
| [Messages](messages-and-timers.md#messages) | yes | Output from `lensMessage` and `lensDump`, plus logs when enabled. |
| [Timers](messages-and-timers.md#timers) | yes | Output from `lensStart`, `lensStop`, `lensMeasure` and `lensAddMeasure`. |
| [Cache](cache.md) | yes | Every BoxCache cache and what the request did to it. |
| [Modules](modules.md) | yes | Loaded modules. |
| [Request](request-and-scopes.md#request) | yes | Request details. |
| [Scopes](request-and-scopes.md#scopes) | yes | Scope contents. |
| [Runtime](jvm.md) | yes | JVM, memory and request statistics. |
| [History](history.md) | yes | The last 50 requests. |

Opt-in collectors: `functions` and `logs`. Custom panels from modules and app code appear after the built-in tabs. Enable or tune collectors under `collectors` in [Configuration](../configuration.md#collectors).

Press `1` to `9` to switch to a tab by position.

## Add your own

Modules and app code can add panels. See [Extending Lens](../guides/extending.md).
