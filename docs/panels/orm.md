---
title: ORM
order: 15
description: The SQL, flushes and failures that bx-orm reports through its events, shown with your other queries, and Hibernate statistics in the console.
icon: lucide:layers
---

# ORM

Lens shows what bx-orm (Hibernate) does. It has two parts.

| Part | Where | Cost |
|---|---|---|
| ORM SQL | The [Queries](queries.md) panel, the Timeline and the Queries console page, labelled ORM | Free |
| ORM page: event totals and Hibernate statistics | The ORM page in the console | BoxLang+ (feature `ormStats`) |

The split is the current state and may change. See [Licensing](../licensing.md).

## An integration, off until you turn it on

ORM is a Lens [integration](../reference/integrations.md): it is **off by default**, it needs the **bx-orm** module (1.7.2 or later), and Lens never reaches into bx-orm or Hibernate. Lens listens to three events that bx-orm announces for tools.

| State | Meaning |
|---|---|
| Not installed | bx-orm is not loaded. The console says "Install bx-orm to enable" and offers no switch. |
| Available, off | bx-orm is installed and `collectors.orm.enabled` is `false`. Lens registers no listener. |
| On | bx-orm is installed and the setting is on. Lens listens. |

An admin turns it on from the Integrations section of the [Modules](../console/modules.md) page or from the ORM page, or sets `collectors.orm.enabled` to `true`. The change is live. If bx-orm is installed after Lens started, the listeners register when the module finishes loading, with no restart.

Turn the setting on before the application starts if you want the DDL that bx-orm runs at startup (for example `dbcreate="dropcreate"`).

## The events

| Event | When | What Lens does with it |
|---|---|---|
| `onORMQuery` | After each JDBC statement. A select fires when its result set closes, once the row count is known. | A query span and an entry in the request's query list, the same as a normal query, labelled ORM with the `kind` (`select`, `insert`, `update`, `delete`, `ddl` or `other`). Counted in the ORM totals. |
| `onORMFlush` | After each Hibernate session flush. | Counted per datasource: flushes and the insert, update and delete statements run during them. |
| `onORMException` | When a statement fails. | Listed under Recent failures on the ORM page. The `onORMQuery` of the same statement carries the error into the request. |

The payloads are described in the bx-orm document `docs/observability-events.md`.

## ORM SQL

Each statement appears with the other queries of the request, with its SQL, time, rows, datasource, kind and the BoxLang line that ran it. Slow query and N+1 detection apply to it like any other query. A failed statement shows its error and counts as a failure in the console Queries statistics.

- **Time.** A select fires when the result set is closed, so the span is as long as the reported `elapsedNanos` and ends when the event arrives. The time excludes reading the rows.
- **Request.** The statement is attached to the request that is current on the thread that ran it. A statement that runs outside a request (startup DDL, a task on its own thread) is counted in the ORM totals only.
- **Parameters.** Values are shown only when the ORM application sets `announceQueryParams` to `true` in `this.ormSettings` **and** `collectors.queries.includeParams` is on in Lens (and `collect.level` is not `light`). Lens cannot switch `announceQueryParams` on. Values can be personal data, so both default to off. Lens never stores parameter values in the statistics or the history of statements.
- **Statement text.** Bound values are `?` in Hibernate SQL. The text is passed through the same redaction as other queries.

## The ORM page

The ORM page shows the integration state with its switch, and then:

- **Totals from the events.** Statements, flushes and failures since Lens started listening, and a row per application and datasource: statements by kind, failed, total and slowest time, flushes with their insert, update and delete counts. This includes startup DDL and statements that ran outside a request. It refreshes every 5 seconds.
- **Recent failures.** The last 20 failed statements with the datasource and the error. Statement text is masked.
- **Hibernate statistics.** Read from bx-orm through `ORMService.getStatistics( appName )`: queries run, the slowest query, entities loaded, inserted, updated and deleted, sessions, flushes, transactions and the second level and query cache counts, per datasource.

Hibernate collects statistics only when the application sets `generateStatistics` in `this.ormSettings`. When it does not, the page says: *statistics are off in this app: set generateStatistics in ormSettings, or turn them on here*. An admin can press **Turn statistics on** (and **Turn statistics off**), which calls `ORMService.setStatisticsEnabled( appName, enabled )`. The counters start from zero when switched on, and the change lasts until the application restarts. The button is for the admin role, is refused in read-only mode, needs the CSRF token and is written to the audit log (`orm.statistics`).

On Free the page shows the integration state and a BoxLang+ note. The statistics route and the switch answer 403.

## Settings

| Setting | Default | Notes |
|---|---|---|
| `collectors.orm.enabled` | `false` | Turns the integration on. Live. Needs bx-orm installed: until both are true Lens registers no listener and does no lookup in bx-orm. |
| `collectors.queries.includeParams` | `false` | Also needed to show parameter values. |
| `collectors.queries.max` | `200` | The cap for query spans of a request counts ORM statements too. Over the cap a statement is still counted in the ORM totals. |

`collectors.orm.statistics` is gone. Lens no longer turns Hibernate statistics on by itself, because bx-orm has its own setting. The setting is ignored if it is still in `boxlang.json` and a saved override of it is dropped with a warning in the log.

In the ORM application (`Application.bx`):

| Setting | Default | Notes |
|---|---|---|
| `this.ormSettings.announceQueryParams` | `false` | Include bound values in `onORMQuery`. |
| `this.ormSettings.generateStatistics` | `false` | Collect Hibernate statistics from startup. |

## Known limits

- It needs bx-orm 1.7.2 or later. Older versions announce no ORM events, so nothing shows. The statistics button also needs that version.
- bx-orm wraps a JDBC connection only if somebody listens at the moment it hands the connection out. Statements on a connection that was already open when you switched the integration on are not seen until it is acquired again.
- Statements that ran before Lens started listening are not counted. Totals are since Lens (or the last reset of the module) started.
- A select reports its time up to the execution, not the time spent reading rows, and its row count when the result set closes.
- `rows` is empty when bx-orm cannot tell (for example DDL).
- A statement that Hibernate flushes at commit reports the line of the commit, not the line that changed the entity.
- Failed statements, including SQL the database rejects while it is prepared, show in the request and in the failures list. The numbers in a failure message are masked.
- Hibernate statistics are global per application and datasource and are not attributed to a request.
- Event totals live in memory: 100 application and datasource pairs and the last 20 failures, nothing on disk.

## Tested with

bx-orm 1.7.2 (snapshot published on ForgeBox and downloads.ortussolutions.com). The harness runs the ORM demo with `WITH_ORM=1`:

```bash
WITH_ORM=1 harness/start.sh                        # downloads bx-orm 1.7.2-snapshot
WITH_ORM=1 BX_ORM_VERSION=1.7.2 harness/start.sh   # a published version
WITH_ORM=1 BX_ORM_DIR=~/bx-orm harness/start.sh    # build the module from a bx-orm checkout
```

`orm.bxm?save=1` inserts and loads, `orm.bxm?fail=1` makes an insert fail.
