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
| Tier 2 extension: Java collectors | Planned. Needs a spike on module class loader parents. |
| Tier 3 extension: custom UI panels | Planned. Will stay off behind `ui.allowCustomPanels`. |
| Compare two requests in History | Planned. |
| Load the full detail of an earlier request from History | Not available in v1. History shows summaries only. |
| BIF call collector | Needs core to announce `postBIFInvocation`. |
| Components panel | Needs core to announce `onComponentInvocation`. |
| Cache hit and miss events | Needs core. Lens uses statistic differences today. |
| SOAP events | Needs core. |
| Bar on core error pages | Needs the web context to announce `onRequestFlushBuffer`. |
| Panels from other modules (ORM, Redis, mail, AI, JDBC pools, Quick, qb, ColdBox, cbwire) | Ideas. |

See [Core gaps](../reference/events.md#core-gaps).

## Not implemented in v1

- `lensDumpHeap`, `lensThreadDump`, heap and thread dumps.
- The `bx:lens` component.
- A `/~bxlens/` asset endpoint. Lens inlines its assets.

## Relation to BX Insights

Lens is request-level and open source. It shows what one request did, right on the page, while you develop.

BX Insights is the separate, licensed observability product. It looks across requests and over time.

Lens will not add persistence. It keeps everything in memory and clears it on restart.

## Design spec

The original draft is at [Design spec](../design/api-spec.md). It has a status section that says what v1 implements.
