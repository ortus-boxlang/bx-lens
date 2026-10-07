# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

----

## [Unreleased]

### Added

* Rewritten core: typed per request model, bounded in-memory request history, access guard (loopback and private networks by default), server side redaction
* Java collectors for templates, functions, queries (N+1 and slow detection), outgoing HTTP, exceptions, transactions, logs, scopes, runtime, BoxCache statistics and loaded modules
* Alpine.js UI injected at the end of HTML responses: health strip, Issues, unified waterfall with zoom, filters, search and editor links, History for HTML, JSON and SSE requests
* `lensMessage`, `lensDump`, `lensStart`, `lensStop`, `lensMeasure`, `lensAddMeasure`, `lensException`, `lensEnable`, `lensDisable`, `lensIsEnabled`, `lensRender` and `lensPanel` BIFs
* Extension API for applications and modules: `onLensRegister`, `onLensCollect`, `onLensRequestStart`, `onLensRequestFinish`
* Demo harness app, Playwright end to end suite, documentation site built with bx-sites

### Changed

* Built against BoxLang 1.19, Alpine.js 3.17, Gradle plugins and GitHub Actions updated
* Lens is disabled by default

### Removed

* Events that do not exist in core (`onException`, `onSOAPRequest`, `onSOAPResponse`), the heap and thread dump BIFs, and the monolithic collector

### Fixed

* The module did not compile, never registered its collectors and read event keys core does not send
