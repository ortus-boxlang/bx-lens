---
title: BX Lens
order: 1
description: A request-level debug bar for BoxLang web apps.
icon: lucide:scan-search
---

# BX Lens

BX Lens shows what your BoxLang web app is doing. It has two surfaces. The **bar** is a docked panel on every HTML page that shows what the request did: templates, queries, HTTP calls, exceptions, timers, scopes and more. The **console** is a standalone page for one server with live requests, errors, queries, datasources, caches, logs, executor health, scheduled tasks, JVM numbers and threads. Both are off by default. Lens runs in MiniServer, servlet containers and CommandBox.

![The BX Lens bar docked at the bottom of a page, showing the Timeline waterfall](assets/screenshots/overview.png)

The bar starts as a collapsed health strip:

![The collapsed health strip with status, time, memory, query and template counts](assets/screenshots/strip-collapsed.png)

::: cards
::: card title="Getting Started" icon="lucide:rocket" href="getting-started.md"
Install the module and enable it in `boxlang.json`.
:::
::: card title="Console" icon="lucide:layout-dashboard" href="console/index.md"
Live requests, errors, queries, datasources, caches, logs, executors, tasks and threads for one server.
:::
::: card title="Configuration" icon="lucide:sliders-horizontal" href="configuration.md"
Every setting, its default and what it does.
:::
::: card title="Panels" icon="lucide:layout-panel-left" href="panels/index.md"
What each tab shows and how Lens flags problems.
:::
::: card title="BIF Reference" icon="lucide:code" href="guides/bifs.md"
`lensMessage`, `lensMeasure`, `lensPanel`, and data functions such as `lensReport` and `lensErrors`.
:::
::: card title="Extending Lens" icon="lucide:puzzle" href="guides/extending.md"
Add your own panels from a module or from app code.
:::
::: card title="Security" icon="lucide:shield" href="security.md"
Off by default, loopback only, roles, redacted output.
:::
::: card title="Licensing" icon="lucide:badge-check" href="licensing.md"
What is free, what needs BoxLang+, and the license states.
:::
:::

## What you get

- A collapsed strip: status, time, query count and time, exception count, the request id and a Console button. It is red for a 5xx or an uncaught exception and has no other color.
- A unified waterfall of templates, functions, queries and HTTP calls on one time axis. A span whose end event never came is drawn striped and marked interrupted.
- In the console, issues per request: slow queries, N+1 patterns, slow templates, caught exceptions and 4xx/5xx responses. The bar only shows what happened.
- BoxLang+: cost per request (CPU time and bytes allocated by the request thread) and a sample of where a slow request was stuck.
- Security notes for missing headers and cookie flags, in the console request detail.
- A history of the last 50 requests (25 on Free) in the console, including JSON and SSE when `history.trackNonHtml` is on. Lens keeps them in memory only.
- A password-protected console with an admin and a view-only role. Pages: Overview, Requests, In flight, Errors, Reports, Ask Lens (with BoxLang+, Lensy), Queries, Executors, Tasks, Datasources, Caches, Logs, Modules, Environment, System (with Run GC), Threads, a Configuration, Bar designer and editable Settings. Task actions, cache values and actions, log download, the diagnostic bundle, heap dumps and saving a bar layout need BoxLang+.
- Optional AI help to explain an error, a query or a deadlock, with a prompt you copy or, with BoxLang+, a call through `bx-ai`, which ships inside Lens. Off by default.
- Light and dark themes, resizing, a floating detached window and keyboard shortcuts.
- A Runtime snapshot tab, an opt-in BIFs panel for time per built-in function, five BIFs that return the console data (`lensReport`, `lensErrors`, `lensQueries`, `lensInflight`, `lensLicense`), and a small API to add your own panels without writing JavaScript.

Free keeps the bar and most of the console. Items marked BoxLang+ need a license or a trial and show a "BoxLang+" note on Free. See [Licensing](licensing.md#free-and-boxlang).

## What it is not

Lens works on one server. It keeps recent requests, errors and reports in memory. With BoxLang+ or a trial, errors and reports are saved to disk and survive restarts. Otherwise it keeps nothing across restarts, apart from the saved bar layout, the saved settings changes and the audit log. BX Insights is the separate observability product for clusters, history over time and alerting. See [Roadmap](project/roadmap.md#relation-to-bx-insights).

!!! warning "Off by default"
    The bar is a development tool. The console can run on a live server, but only with a password, an access list and HTTPS. See [Security](security.md) and [Running Lens in production](guides/production.md).

## License and status

BX Lens is a product of Ortus Solutions. License terms apply, see the [BoxLang+ plans page](https://boxlang.io/plans) and [Licensing](licensing.md). The original [design spec](design/api-spec.md) has a status section. Lens needs BoxLang 1.19 or newer and Java 21.
