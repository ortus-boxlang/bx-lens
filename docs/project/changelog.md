---
title: Changelog
order: 4
description: Notable changes to BX Lens.
icon: lucide:list
---

# Changelog

This project follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) and [Semantic Versioning](https://semver.org/spec/v2.0.0.html). The source file is `changelog.md` in the repository.

## Unreleased

### Added

- Five BIFs that return the console data as plain structs and arrays, in any request: `lensReport`, `lensErrors`, `lensQueries`, `lensInflight` and `lensLicense`. See [Data functions](../guides/bifs.md#data-functions).
- The [BIFs panel](../panels/bifs.md) and the `collectors.bifs` collector: time per built-in function, from `postBIFInvocation`. It is heavy and off by default. It needs a BoxLang build with core pull request 657.
- The [Modules page](../console/modules.md) in the console: every loaded module, nested ones too, with version, state, path and what it provides.
- `/~bxlens/` with a trailing slash opens the console. On MiniServer it also needs a pass predicate, see [The console URL](../guides/production.md#the-console-url).
- Live settings: the console Settings page is editable. Changes apply at once, are saved to `config/bxlens-settings.json` (or `console.overridesFile`), survive restarts and win over `boxlang.json`, with a reset per setting. `console.readOnly` refuses every change from the console. See [Console settings](../console/settings.md) and [Live settings](../configuration.md#live-settings).
- Roles and access: `console.viewerPassword` for a view-only role, proxy header trust (`access.trustProxyHeader`, `access.proxyHeader`, `access.proxyPeers`), `console.requireHttps` with a warning banner for plain HTTP, and an audit log, `bxlens-audit.log`. See [Security](../security.md).
- Console pages: [Datasources](../console/datasources.md), [Caches](../console/caches.md), [Logs](../console/logs.md), [Environment](../console/environment.md) with a diagnostic bundle, [In flight and Queries](../console/in-flight-and-queries.md), [Errors and Reports](../console/errors-and-reports.md) and [Ask Lens](../console/ask-lens-and-ai.md).
- System page: Run GC, heap dump (`console.allowHeapDump`, admin only, off by default) and a deadlock banner that shows the cycle. See [System and Threads](../console/system-and-threads.md).
- Plus disk store: with BoxLang+ or a trial, errors and reports are saved to disk and totals since first install survive restarts. Without a license the request history is capped at 25, errors and reports stay in memory, and the server side AI calls are off. This split is the current state and may change. See [Licensing](../licensing.md#free-and-boxlang).
- Optional AI help (`ai.*`): copy a redacted prompt, open ChatGPT or Claude, and Explain with AI and Ask Lens through the `bx-ai` module. `bx-ai` 3.4.0 ships inside the module, in `modules/bxai`, so there is nothing to install. Lens still loads if it is removed. The server calls need BoxLang+ or a trial.
- New settings under `console`, `access`, `store`, `ai` and `collectors`. `checks.*` and `dev.*` are now declared in the schema. See [Configuration](../configuration.md).
- A standalone console at `/~bxlens/index.bxm` with a password login, per-address lockout, CSRF protection, a strict Content-Security-Policy and no external requests. Pages: Overview, Requests, Executors, Tasks, System, Threads, Bar designer and Settings. See [Console](../console/index.md).
- Live executor health from BoxLang's own health reports, and scheduled tasks with status, metrics, Run now, pause, resume and reload.
- A Server-Sent Events stream for live data, with polling as the fallback.
- Bar designer: choose and order the bar's tabs. The layout is saved in `config/bxlens-layout.json`.
- Bar: Open console button and a More menu (open in console, copy request JSON, copy id, detach).
- Request cost (CPU time and bytes allocated by the request thread), response headers in the Request panel, a slow request sample that names the template and line, and security Notes for missing headers and cookie flags.
- `collect.level` (`off`, `light`, `full`) and `tabs.hide`.
- License detection through `bx-plus`, with Plus, Trial, Expired and Free states. See [Licensing](../licensing.md) for what Free and BoxLang+ include.
- Phosphor icons, vendored (MIT).
- Request-level debug bar for BoxLang web apps. It is off by default and limited to local and private callers.
- Panels: Issues, Timeline, Queries, Templates, HTTP, Exceptions, Messages, Timers, Cache, Modules, Request, Scopes, Runtime and History.
- Issues engine for N+1, slow queries, slow templates and functions, slow requests, HTTP 4xx and 5xx, and exceptions.
- BIFs: `lensMessage`, `lensDump`, `lensStart`, `lensStop`, `lensMeasure`, `lensAddMeasure`, `lensException`, `lensEnable`, `lensDisable`, `lensIsEnabled`, `lensRender` and `lensPanel`.
- Extension points `onLensRegister`, `onLensRequestStart`, `onLensCollect` and `onLensRequestFinish`, with built-in renderers.
- In-memory History of the last 50 requests, including JSON and SSE.
- Floating detach window, resizing, keyboard shortcuts, light and dark themes, and persisted UI state.
- Server-side redaction and size limits.
- Demo harness, Playwright end to end tests and generated documentation screenshots.
- Documentation site built with bx-sites.

### Changed

- The bar and the console are switched independently, and both are off by default. `enabled` became `bar.enabled`, and `access.allowedIPs` and `access.allowPrivateNetworks` became `bar.access`. The default `bar.access` is loopback only. See [Moving from older settings](../configuration.md#moving-from-older-settings).
- BX Lens is a product of Ortus Solutions under the BoxLang+ proprietary license (freeware with limits) and is no longer described as open source. See [Licensing](../licensing.md).
- The queries collector id and the console Queries page share `collectors.queries.enabled`. The `logs` collector (per request) is separate from `logfiles` (the console Logs page).
- `X-Forwarded-Proto` is believed only from a trusted proxy peer.

### Not in v1

- `lensDumpHeap` and the `bx:lens` component. Thread dumps and heap dumps are in the console.
- Java collectors and custom UI extensions.
- Comparing requests, and loading the detail of an earlier request from History.
- SOAP events. They need core changes.
