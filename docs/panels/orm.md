---
title: ORM
order: 15
description: The SQL that bx-orm runs, shown with your other queries, and Hibernate statistics in the console.
icon: lucide:layers
---

# ORM

Lens shows the SQL that bx-orm (Hibernate) runs. It has two parts.

| Part | Where | Cost |
|---|---|---|
| ORM SQL | The [Queries](queries.md) panel, the Timeline and the Queries console page, labelled ORM | Free |
| ORM statistics | The ORM page in the console | BoxLang+ (feature `ormStats`) |

The split is the current state and may change. See [Licensing](../licensing.md).

## ORM SQL

Each statement bx-orm sends to the database appears with the other queries of the request, with its SQL, bindings, time, rows, datasource and the BoxLang line. An ORM statement carries an ORM label. Slow query and N+1 detection apply to it like any other query.

## ORM statistics

The ORM page in the console shows Hibernate statistics for each session factory: queries run, the slowest query, entities loaded, inserted, updated and deleted, sessions, flushes, transactions, second level cache and query cache counts, and a table per HQL or criteria query. On Free the page shows a BoxLang+ note.

## Settings

| Setting | Default | Notes |
|---|---|---|
| `collectors.orm.enabled` | `true` | Turns the ORM SQL on or off. Does nothing when bx-orm is not installed. |
| `collectors.orm.statistics` | `true` | Turns on Hibernate statistics for each session factory. Locked: set it in `boxlang.json` only. |

## How it works

Hibernate sends SQL to the database without any BoxLang event. Lens swaps the Hibernate connection provider of each session factory for a JDK proxy that wraps the connection, statement and result set. It reads bx-orm and Hibernate by reflection, so it needs no dependency on them.

If the swap fails, Lens falls back to a Logback appender on `org.hibernate.SQL`. That gives the SQL text only, with no time, rows or bindings. The mode in use shows as SQL capture on the ORM page.

## Known limits

- The swap sets a private Hibernate 5 field by reflection. A different Hibernate version may need the Logback fallback.
- SQL that runs at startup, such as schema DDL, is not captured. Lens hooks a session factory after it is built.
- The row count of a select grows as the result set is read, so it is final only after the code has consumed the result set.
- A statement that Hibernate flushes at commit reports the transaction line, not the line that changed the entity.
- Statistics are global per session factory. They are totals since the factory started and are not attributed to a request.

## Tested with

bx-orm 1.3.0 and 1.7.1, only through a spike. The harness runs the ORM demo with `WITH_ORM=1`, which installs bx-orm (default `1.7.1`, change it with `BX_ORM_VERSION`):

```bash
WITH_ORM=1 harness/start.sh
```
