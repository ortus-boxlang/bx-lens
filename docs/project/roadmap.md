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

## Not implemented

- `lensDumpHeap` and heap dumps. Thread dumps are in the console Threads page.
- The `bx:lens` component.
- A feature split between free and BoxLang+. It is not decided, see [Licensing](../licensing.md).

## Relation to BX Insights

Lens works on one server. The bar shows what one request did, right on the page. The console shows live requests, executors, tasks, JVM numbers and threads for that server.

BX Insights is the separate observability product. It is the answer for clusters, history over time and alerting.

Lens will not add persistence or cross-node views. It keeps requests in memory and clears them on restart. The only file it writes is the saved bar layout.

## Design spec

The original draft is at [Design spec](../design/api-spec.md). It has a status section that says what v1 implements.
