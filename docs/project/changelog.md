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

- A standalone console at `/~bxlens/index.bxm` with a password login, per-address lockout, CSRF protection, a strict Content-Security-Policy and no external requests. Pages: Overview, Requests, Executors, Tasks, System, Threads, Bar designer and Settings. See [Console](../console/index.md).
- Live executor health from BoxLang's own health reports, and scheduled tasks with status, metrics, Run now, pause, resume and reload.
- A Server-Sent Events stream for live data, with polling as the fallback.
- Bar designer: choose and order the bar's tabs. The layout is saved in `config/bxlens-layout.json`.
- Bar: Open console button and a More menu (open in console, copy request JSON, copy id, detach).
- Request cost (CPU time and bytes allocated by the request thread), response headers in the Request panel, a slow request sample that names the template and line, and security Notes for missing headers and cookie flags.
- `collect.level` (`off`, `light`, `full`) and `tabs.hide`.
- License detection through `bx-plus`, with Plus, Trial, Expired and Free states. Every feature is available in every state for now. See [Licensing](../licensing.md).
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
- BX Lens is a product of Ortus Solutions and is no longer described as open source. See [Licensing](../licensing.md).

### Not in v1

- `lensDumpHeap` and the `bx:lens` component. Thread dumps are in the console.
- Java collectors and custom UI extensions.
- Comparing requests, and loading the detail of an earlier request from History.
- A BIF call collector and SOAP events. Both need core changes.
