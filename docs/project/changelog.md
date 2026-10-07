---
title: Changelog
order: 4
description: Notable changes to BX Lens.
icon: lucide:list
---

# Changelog

This project follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) and [Semantic Versioning](https://semver.org/spec/v2.0.0.html). The source file is [changelog.md](https://github.com/ortus-boxlang/bx-lens/blob/main/changelog.md) in the repository.

## Unreleased

### Added

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

### Not in v1

- `lensDumpHeap`, `lensThreadDump` and the `bx:lens` component.
- Java collectors and custom UI extensions.
- Comparing requests, and loading the detail of an earlier request from History.
- A BIF call collector and SOAP events. Both need core changes.
