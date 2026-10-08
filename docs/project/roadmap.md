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

- The `lensDumpHeap` BIF. Heap dumps are in the console System page and are off by default. Thread dumps are in the console Threads page.
- The `bx:lens` component.
- A final feature split between free and BoxLang+. Today the disk store, the long request history and AI calls from the server need Plus or a trial, and this may change, see [Licensing](../licensing.md#free-and-boxlang).

## Relation to BX Insights

Lens works on one server. The bar shows what one request did, right on the page. The console shows live requests, executors, tasks, JVM numbers and threads for that server.

BX Insights is the separate observability product. It is the answer for clusters, history over time and alerting.

Lens will not add cross-node views or a database. It keeps requests in memory and clears them on restart. With BoxLang+ or a trial it saves errors and reports to disk so they survive restarts. Besides that it writes only the saved bar layout, the saved settings changes and the audit log.

## Design spec

The original draft is at [Design spec](../design/api-spec.md). It has a status section that says what v1 implements.
