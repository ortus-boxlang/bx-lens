# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

----

## [Unreleased]

## [1.0.0] - 2026-10-08

First release. BX Lens is a commercial Ortus Solutions product. Report issues in the BLMODULES Jira project.

### Added

* Standalone console at `/~bxlens/index.bxm`: password login (`bxsecret:` value), in-memory sessions, CSRF token, per-IP lockout, strict Content-Security-Policy, no external requests. Pages: Overview, Requests, Executors, Tasks, System, Threads, Bar designer, Settings
* Executors page with live health reports and thresholds, Tasks page with status, metrics, Run now, pause, resume and reload, System and Threads pages with a thread dump download
* Server-Sent Events stream for live console data, with polling as the fallback
* Bar designer: toggle and reorder bar tabs, saved to `config/bxlens-layout.json`; Open console button and More menu in the bar
* Request cost (CPU time and bytes allocated), response headers in the Request panel, slow request sample that names the template and line, security Notes (`info` severity) for missing headers and cookie flags
* `collect.level` (`off`, `light`, `full`) and `tabs.hide`
* License detection through `bx-plus` (Plus, Trial, Expired, Free), `dev.license` to force a state. All features stay available in every state until the split is decided
* Phosphor icons (MIT) vendored in place of ad hoc glyphs
* Harness: console password, tasks, `demo-pool`, `load.bxm`, `stall.bxm`, `LENS_LICENSE`
* Rewritten core: typed per request model, bounded in-memory request history, access guard (loopback and private networks by default), server side redaction
* Java collectors for templates, functions, queries (N+1 and slow detection), outgoing HTTP, exceptions, transactions, logs, scopes, runtime, BoxCache statistics and loaded modules
* Alpine.js UI injected at the end of HTML responses: health strip, Issues, unified waterfall with zoom, filters, search and editor links, History for HTML, JSON and SSE requests
* `lensMessage`, `lensDump`, `lensStart`, `lensStop`, `lensMeasure`, `lensAddMeasure`, `lensException`, `lensEnable`, `lensDisable`, `lensIsEnabled`, `lensRender` and `lensPanel` BIFs
* Extension API for applications and modules: `onLensRegister`, `onLensCollect`, `onLensRequestStart`, `onLensRequestFinish`
* Live settings: the console Settings page is editable. Changes apply at once, are saved to `config/bxlens-settings.json` (or `console.overridesFile`), survive restarts and win over `boxlang.json`, with a reset per setting. `SettingsRegistry` defines which settings are live. `console.readOnly` refuses every change from the console
* Roles and access: `console.viewerPassword` for a view-only role (no changes, no download of thread dumps, heap dumps, log files or the bundle, no cache values). `access.trustProxyHeader`, `access.proxyHeader` and `access.proxyPeers` read the client address from a proxy header only from a trusted peer, and never let a remote peer claim loopback. `console.requireHttps` and a warning banner for plain HTTP. An audit log, `bxlens-audit.log`
* Console pages: Datasources (Hikari pool numbers, timings, connection test), Caches (statistics, key list capped at 100, value view cut at 2 KB, evict, reap, clear), Logs (all files in the logs directory, search, level filter, live tail, admin download, path checks), Environment (configuration, modules, JVM arguments, variables, properties, secrets hidden) with a diagnostic bundle zip, In flight (running requests with a live stack), Queries (per statement runs, average, maximum, total, failures, slow), Errors (grouped by fingerprint, redacted samples), Reports (totals, p50, p95 and p99, status classes, URLs, minute series) and Ask Lens
* System page: Run GC, heap dump (`console.allowHeapDump`, admin only, confirmation, disk space check, one at a time, removed after download or after 10 minutes) and a deadlock banner that shows the cycle
* Plus disk store: with BoxLang+ or a trial, errors and reports are saved atomically to `errors.json` and `reports.json` (`store.enabled`, `store.dir`, `store.retentionHours`, `store.maxMB`, `store.flushSeconds`) and totals since first install survive restarts. Without a license the request history is capped at 25, errors and reports stay in memory, and the server side AI calls are off. This split is the current state and may change
* Optional AI help (`ai.*`): copy a redacted prompt, open ChatGPT or Claude (copies the prompt, sends nothing from the server), and Explain with AI and Ask Lens through the `bx-ai` module. `bx-ai` is a soft dependency. Checked with `bx-ai` 3.0.0 and a mock Ollama server (`harness/mock-ai.py`); `bx-ai` 2.0.0 fails to start on the current BoxLang snapshot
* New settings: `console.viewerPassword`, `console.requireHttps`, `console.readOnly`, `console.overridesFile`, `console.allowHeapDump`, `access.trustProxyHeader`, `access.proxyHeader`, `access.proxyPeers`, `store.*`, `ai.*`, and collectors `datasources`, `caches`, `logfiles`, `environment`, `queries`, `inflight`, `errors`, `reports` and `ask`. `checks.*` and `dev.*` are now declared in the schema
* Demo harness app, Playwright end to end suite, documentation site built with bx-sites

### Changed

* **Breaking:** `enabled` is now `bar.enabled`. `access.allowedIPs` and `access.allowPrivateNetworks` are now `bar.access` (use the word `private`). The default is loopback only. See docs/configuration.md
* BX Lens is a product of Ortus Solutions and no longer described as open source
* The queries collector id and the console Queries page share `collectors.queries.enabled`. The `logs` collector (per request) is separate from `logfiles` (the console Logs page)
* `X-Forwarded-Proto` is believed only from a trusted proxy peer
* Built against BoxLang 1.19, Alpine.js 3.17, Gradle plugins and GitHub Actions updated
* Lens is disabled by default

### Removed

* Events that do not exist in core (`onException`, `onSOAPRequest`, `onSOAPResponse`), the heap and thread dump BIFs, and the monolithic collector

### Fixed

* The module did not compile, never registered its collectors and read event keys core does not send
