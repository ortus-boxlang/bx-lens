---
title: Roadmap
order: 1
description: What is planned for BX Lens and how it relates to BX Insights.
icon: lucide:map
---

# Roadmap

!!! note
    Items on this page are planned, not shipped.

## Planned

| Item | Status |
|---|---|
| Tier 2 extension: Java collectors | Planned for v1.x. Needs a spike on module class loader parents. |
| Tier 3 extension: custom UI panels | Planned for later. Will stay off behind `ui.allowCustomPanels`. |
| Compare two requests in History | Planned after v1. |
| First-party panels from modules (ORM, Redis, mail, AI, JDBC pools, Quick, qb, ColdBox, cbwire) | Candidates. |
| Core changes for `postBIFInvocation`, `onComponentInvocation`, cache hit and miss events, SOAP events | Requested from core. See [Core gaps](../reference/events.md#core-gaps). |

## Open questions

The spec lists these as undecided:

- Whether History survives an app reinit or resets with it.
- Whether editor path mapping (`remoteBase` to `localBase`) stays in v1.
- Whether the servlet and CommandBox paths run the same `onRequestEnd` flow as MiniServer.

## Relation to BX Insights

Lens is request-level and open source. It shows what one request did, right on the page, while you develop.

BX Insights is the separate commercial observability product. It looks across requests and over time.

Lens will not add persistence. It keeps everything in memory and clears it on restart.

## Design spec

The full draft is at [Design spec](../design/api-spec.md).
