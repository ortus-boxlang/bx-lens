---
title: Panels
order: 1
description: A tour of every tab of the bar.
icon: lucide:layout-panel-left
---

# Panels

The bar is a snapshot of what happened in **this** request. It shows what ran and how long it took. It does not judge: there are no issues, no N+1 or slow flags and no amber colors. The [console](../console/index.md) keeps the issues, the history and the views of the whole server, and shows the issues of a request in its detail page.

Each panel comes from one or more collectors. Collectors never throw into your request. If one fails, Lens logs the error to the `bxLens` logger and skips that panel for the request.

| Panel | On by default | What it shows |
|---|---|---|
| [Timeline](timeline.md) | yes | One waterfall for templates, functions, queries, HTTP calls, transactions and timers. A span whose end event never came is marked interrupted. |
| [Queries](queries.md) | yes | SQL, timing, rows and, when you turn it on, the bound values. |
| [Exceptions](exceptions.md) | yes | Thrown and caught exceptions. |
| [HTTP](http.md) | yes | Outgoing HTTP calls. |
| [Messages](messages-and-timers.md#messages) | yes | Output from `lensMessage` and `lensDump`, plus logs when enabled. |
| [Timers](messages-and-timers.md#timers) | yes | Output from `lensStart`, `lensStop`, `lensMeasure` and `lensAddMeasure`. |
| [Request](request-and-scopes.md#request) | yes | Request and response headers, and the CPU time and memory the request cost (BoxLang+). |
| [Scopes](request-and-scopes.md#scopes) | no | Scope contents. Opt in with `collectors.scopes`. Not at `collect.level` `light`. |
| [BIFs](bifs.md) | no | Time spent in built-in functions. Costs time on every BIF call. |
| [Runtime](runtime.md) | yes | A small snapshot of the runtime: versions, system, uptime, heap, cache names and the main settings. |

The strip above the tabs shows the status, the request time, the number of queries and their time, the number of exceptions, the request id and a **Console** button that opens this request in the console. It is red for a 5xx response or an exception nothing caught, and has no other color.

The console has its own pages. See [Console](../console/index.md). The issues the old Issues tab listed (N+1, slow queries, slow templates, error statuses, security notes) are now part of the console [request detail](../console/issues.md).

Opt-in collectors: `functions`, `logs`, `bifs`, `scopes`. Custom panels from modules and app code appear after the built-in tabs. Enable or tune collectors under `collectors` in [Configuration](../configuration.md#collectors).

Press `1` to `9` to switch to a tab by position. Hide tabs with `tabs.hide`, or reorder them in the [Bar designer](../console/bar-designer.md).

## Add your own

Modules and app code can add panels. See [Extending Lens](../guides/extending.md).
