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
* Executors page with live health reports and thresholds, Tasks page with status and metrics (Run now, pause, resume and reload need BoxLang+), System and Threads pages with a thread dump download (free)
* Server-Sent Events stream for live console data, with polling as the fallback
* Bar designer: toggle and reorder bar tabs, saved to `config/bxlens-layout.json` (saving needs BoxLang+); Open console button and More menu in the bar
* Request cost (CPU time and bytes allocated, BoxLang+), response headers in the Request panel, slow request sample that names the template and line (BoxLang+), security Notes (`info` severity) for missing headers and cookie flags
* `collect.level` (`off`, `light`, `full`) and `tabs.hide`
* License detection through `bx-plus` (Plus, Trial, Expired, Free), `dev.license` to force a state. See the free and Plus split below
* Phosphor icons (MIT) vendored in place of ad hoc glyphs
* Harness: console password, tasks, `demo-pool`, `load.bxm`, `stall.bxm`, `LENS_LICENSE`
* Rewritten core: typed per request model, bounded in-memory request history, access guard (loopback and private networks by default), server side redaction
* Java collectors for templates, functions, queries (N+1 and slow detection), outgoing HTTP, exceptions, transactions, logs, scopes, runtime, BoxCache statistics and loaded modules
* Alpine.js UI injected at the end of HTML responses: health strip, Issues, unified waterfall with zoom, filters, search and editor links, History for HTML, JSON and SSE requests
* `lensMessage`, `lensDump`, `lensStart`, `lensStop`, `lensMeasure`, `lensAddMeasure`, `lensException`, `lensEnable`, `lensDisable`, `lensIsEnabled`, `lensRender` and `lensPanel` BIFs
* Extension API for applications and modules: `onLensRegister`, `onLensCollect`, `onLensRequestStart`, `onLensRequestFinish`
* Live settings: the console Settings page is editable. Changes apply at once, are saved to `config/bxlens-settings.json` (or `console.overridesFile`), survive restarts and win over `boxlang.json`, with a reset per setting. `SettingsRegistry` defines which settings are live. `console.readOnly` refuses every change from the console
* Roles and access: `console.viewerPassword` for a view-only role (no changes, no download of thread dumps, heap dumps, log files or the bundle, no cache values). `access.trustProxyHeader`, `access.proxyHeader` and `access.proxyPeers` read the client address from a proxy header only from a trusted peer, and never let a remote peer claim loopback. `console.requireHttps` and a warning banner for plain HTTP. An audit log, `bxlens-audit.log`
* Console pages: Datasources (Hikari pool numbers, timings, connection test), Caches (statistics, key list capped at 100, value view cut at 2 KB, evict, reap and clear need BoxLang+), Logs (all files in the logs directory, search, level filter, live tail, path checks, admin download needs BoxLang+), Environment (configuration, modules, JVM arguments, variables, properties, secrets hidden), with a diagnostic bundle zip (BoxLang+), In flight (running requests with a live stack), Queries (per statement runs, average, maximum, total, failures, slow), Errors (grouped by fingerprint, redacted samples), Reports (totals, p50, p95 and p99, status classes, URLs, minute series) and Ask Lens
* System page: Run GC, heap dump (BoxLang+, `console.allowHeapDump`, admin only, confirmation, disk space check, one at a time, removed after download or after 10 minutes) and a deadlock banner that shows the cycle
* Free and BoxLang+ split. Free keeps the bar and most of the console. BoxLang+ or a trial adds request cost and the slow request sample, task actions, cache value, evict, reap and clear, log download, the diagnostic bundle, heap dumps, saving or resetting a bar layout, AI calls from the server, the disk store (`errors.json` and `reports.json` saved atomically, totals since first install, a longer minute series; `store.enabled`, `store.dir`, `store.retentionHours`, `store.maxMB`, `store.flushSeconds`) and a request history longer than 25. A locked item shows a "BoxLang+" note and the server answers 403 (AI answers 409). The split is the current state and may change.
* Optional AI help (`ai.*`): copy a redacted prompt, open ChatGPT or Claude (copies the prompt, sends nothing from the server), and Explain with AI and Ask Lens through the `bx-ai` module. `bx-ai` 3.4.0 ships inside the module in `modules/bxai` and is downloaded by the build (`downloadBxAi`). Checked with a mock Ollama server (`harness/mock-ai.py`). Lens still loads if it is removed
* New settings: `console.viewerPassword`, `console.requireHttps`, `console.readOnly`, `console.overridesFile`, `console.allowHeapDump`, `access.trustProxyHeader`, `access.proxyHeader`, `access.proxyPeers`, `store.*`, `ai.*`, and collectors `datasources`, `caches`, `logfiles`, `environment`, `queries`, `inflight`, `errors`, `reports` and `ask`. `checks.*` and `dev.*` are now declared in the schema
* BIFs that return the console data as plain structs and arrays, in any request: `lensReport()`, `lensErrors( limit=20 )`, `lensQueries( limit=20, sort="slowest" )`, `lensInflight()` and `lensLicense()`. The harness page `api/lens.json.bxm` shows them
* BIFs tab in the bar and the `collectors.bifs` collector: calls, total, average and slowest time and errors per built-in function, from `postBIFInvocation` and `onBIFException`. Heavy and off by default, because every BIF call allocates an event while it is on. Lens's own `lens*` functions are left out, a request keeps up to 300 names and the list shows the top 60 by total time. Needs a BoxLang build with core pull request 657
* Modules page in the console: every loaded module, nested ones included, with version, state, author, activation time, path, public mapping, parent, dependencies and what it provides
* `/~bxlens/` with a trailing slash opens the console (`IndexRewrite.bx`). On MiniServer it also needs a pass predicate, see docs/guides/production.md
* Demo harness app, Playwright end to end suite, documentation site built with bx-sites

### Changed

* **Breaking:** `enabled` is now `bar.enabled`. `access.allowedIPs` and `access.allowPrivateNetworks` are now `bar.access` (use the word `private`). The default is loopback only. See docs/configuration.md
* BX Lens is a product of Ortus Solutions under the BoxLang+ proprietary license (freeware with limits) and no longer described as open source. New Java files carry the four line BoxLang+ header
* The queries collector id and the console Queries page share `collectors.queries.enabled`. The `logs` collector (per request) is separate from `logfiles` (the console Logs page)
* `X-Forwarded-Proto` is believed only from a trusted proxy peer
* Built against BoxLang 1.19, Alpine.js 3.17, Gradle plugins and GitHub Actions updated
* Lens is disabled by default

### Removed

* Events that do not exist in core (`onException`, `onSOAPRequest`, `onSOAPResponse`), the heap and thread dump BIFs, and the monolithic collector

### Fixed

* The module did not compile, never registered its collectors and read event keys core does not send
