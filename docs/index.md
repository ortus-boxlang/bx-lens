---
title: BX Lens
order: 1
description: A request-level debug bar for BoxLang web apps.
icon: lucide:scan-search
---

# BX Lens

BX Lens is an open source debug bar for BoxLang web apps. Turn it on and every HTML page gets a docked panel that shows what the request did: templates, queries, HTTP calls, exceptions, timers, scopes and more. It runs in MiniServer, servlet containers and CommandBox.

![The BX Lens bar docked at the bottom of a page, showing the Timeline waterfall](assets/screenshots/overview.png)

The bar starts as a collapsed health strip:

![The collapsed health strip with status, time, memory, query and template counts](assets/screenshots/strip-collapsed.png)

::: cards
::: card title="Getting Started" icon="lucide:rocket" href="getting-started.md"
Install the module and enable it in `boxlang.json`.
:::
::: card title="Configuration" icon="lucide:sliders-horizontal" href="configuration.md"
Every setting, its default and what it does.
:::
::: card title="Panels" icon="lucide:layout-panel-left" href="panels/index.md"
What each tab shows and how Lens flags problems.
:::
::: card title="BIF Reference" icon="lucide:code" href="guides/bifs.md"
`lensMessage`, `lensMeasure`, `lensPanel` and the rest.
:::
::: card title="Extending Lens" icon="lucide:puzzle" href="guides/extending.md"
Add your own panels from a module or from app code.
:::
::: card title="Security" icon="lucide:shield" href="security.md"
Off by default, localhost only, redacted output.
:::
:::

## What you get

- A collapsed health strip: status, time, memory, query and template counts.
- A unified waterfall of templates, functions, queries and HTTP calls on one time axis.
- An Issues tab that ranks slow queries, N+1 patterns, slow templates, caught exceptions and 4xx/5xx responses.
- A History tab with the last 50 requests, including JSON and SSE. Lens keeps them in memory only.
- Light and dark themes, resizing, a floating detached window and keyboard shortcuts.
- Cache and Modules panels, and a small API to add your own panels without writing JavaScript.

## What it is not

Lens is request-level and open source. It stores nothing on disk and keeps no data across restarts. BX Insights is the separate, licensed observability product for data across requests and over time. See [Roadmap](project/roadmap.md#relation-to-bx-insights).

!!! warning "Development tool"
    Lens ships disabled. Never enable it in production. See [Security](security.md).

## Source and status

BX Lens lives at [github.com/ortus-boxlang/bx-lens](https://github.com/ortus-boxlang/bx-lens). The original [design spec](design/api-spec.md) has a status section that says what v1 implements. Lens needs BoxLang 1.19 or newer and Java 21.
