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
* Demo harness app, Playwright end to end suite, documentation site built with bx-sites

### Changed

* **Breaking:** `enabled` is now `bar.enabled`. `access.allowedIPs` and `access.allowPrivateNetworks` are now `bar.access` (use the word `private`). The default is loopback only. See docs/configuration.md
* BX Lens is a product of Ortus Solutions and no longer described as open source
* Built against BoxLang 1.19, Alpine.js 3.17, Gradle plugins and GitHub Actions updated
* Lens is disabled by default

### Removed

* Events that do not exist in core (`onException`, `onSOAPRequest`, `onSOAPResponse`), the heap and thread dump BIFs, and the monolithic collector

### Fixed

* The module did not compile, never registered its collectors and read event keys core does not send
