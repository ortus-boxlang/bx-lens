---
title: Core Event Inventory
order: 1
description: The BoxLang core 1.19 events Lens listens to and the payload keys each one sends.
icon: lucide:radio
---

# Core Event Inventory

This page is for module authors. It lists the BoxLang core 1.19 events that Lens uses, with the payload keys core actually sends. The data comes from the [design spec](../design/api-spec.md), which read them from the core source. Lens does not use every event listed here. See the spec status section for what v1 implements.

## Request lifecycle

Core announces these globally and per application.

| Event | Payload | Use |
|---|---|---|
| `onRequestStart` | `context, args, application, listener` | Create the Lens request and check access. |
| `onRequest` | same | Template or class entry. |
| `onRequestEnd` | same | Finalize, store and inject. Core skips it on unhandled exceptions and aborts. |
| `onError` | same, `args` holds the exception | Capture the exception, finalize and inject on error pages. |
| `onAbort` | same | Finalize and inject on abort. |
| `onMissingTemplate` | same | 404 issue. |
| `onSessionStart`, `onSessionEnd` | same | Session panel. |
| `onRequestFlushBuffer` | `context, output` (mutable) | Alternative injection point. It fires on every flush, so only the last flush may be modified. |
| `onApplicationStart/End/Restart/Defined`, `beforeApplicationListenerLoad`, `afterApplicationListenerLoad` | app data | Application data. |
| `onWebExecutorRequest` (web-support) | `context, appListener, requestString, exchange, updatedRequest` | Route rewrites and request identity. |

## Execution

| Event | Payload | Use |
|---|---|---|
| `preTemplateInvoke`, `postTemplateInvoke` | `context, template, templatePath` | Template tree. No `executionTime`, so Lens times it. |
| `preFunctionInvoke`, `postFunctionInvoke` | `context, arguments, function, name` (+ `result` on post) | Functions panel (opt in, hot path). |
| `onFunctionException` | same + `exception` | Exceptions. |
| `onBIFInvocation` | `context, arguments, bif, name` | Announced before a BIF runs. Lens does not use it. |
| `postBIFInvocation` | `name`, `elapsedNanos` (the keys Lens reads) | Time per built-in function. Used by the opt-in `bifs` collector. Core announces it only when a listener exists. |
| `onBIFException` | `name`, `elapsedNanos` (the keys Lens reads) | Counts failed BIF calls in the `bifs` collector. |
| `onPreSourceInvoke`, `onPostSourceInvoke` | script or source execution | Eval and script timing. |
| `afterBoxClassCreation`, `afterBoxClassInit`, `afterDynamicObjectCreation`, `onCreateObjectRequest` | object created | Objects (opt in). |
| `onComponentInstance`, `onBIFInstance` | descriptor | Startup only. |
| `onComponentInvocation` | declared, never announced | Unusable today. |

## Data

| Event | Payload | Use |
|---|---|---|
| `onQueryBuild` | query build data | Builder usage. |
| `preQueryExecute` | `sql, bindings, pendingQuery, context` | Start time and SQL text. |
| `postQueryExecute` | `sql, bindings, executionTime, data, result (meta), pendingQuery, executedQuery, context` | Queries panel. Row count comes from `data`, datasource from `pendingQuery`. |
| `queryAddRow` | query row | Ignored. |
| `onTransactionBegin/Acquire/Commit/Rollback/SetSavepoint/Release/End` | `connection, transaction, context` | Spans on the waterfall. |
| `onDatasourceConfigLoad`, `onDatasourceStartup`, `onDatasourceInitialized`, service start and stop | datasource | JVM and runtime panel. |
| `afterCacheElementInsert/Updated/Removed`, `beforeCacheElementRemoved`, `afterCacheClearAll` | cache element | Cache writes only. No read, hit or miss event. |
| `onCacheComponentAction` | cache component action | Output cache usage. |

## I/O and misc

| Event | Payload | Use |
|---|---|---|
| `onHTTPRequest` | `result, httpClient, httpRequest` | HTTP call start. |
| `onHTTPRawResponse`, `onHTTPResponse` | `result, response, httpClient, httpRequest` | Status, size and time. |
| `onHTTPError` | error data | Issue. |
| `onFileComponentAction` | file action | Files (opt in). |
| `logMessage` | `text, log, type` | Logs panel. |
| `onBXDump`, `onMissingDumpOutput` | dump data | Route `bx:dump` output to Messages. |
| `beforeObjectMarshallSerialize` and similar | serialization | Ignored. |

## Global events

These are not per request. Lens uses two of them, and only when the console is on, to remember how the last run of each scheduled task ended:

| Event | Use |
|---|---|
| `schedulerOnAnyTaskSuccess` | Remember a successful run. |
| `schedulerOnAnyTaskError` | Remember the error message and stack of a failed run. |

Core keeps run counts for a task but not the error of its last run, so the Tasks page keeps it in memory since Lens started. Everything else on the console Executors, Tasks, System and Threads pages is read on demand from core's async and scheduler services and the JDK management beans, not from events.

## Spans that never close

A span opens on a `pre` event and closes on the matching `post` event. Core does not always fire the `post` event. This is what the harness pages in `harness/app/spans` showed on core 1.19, checked by the end to end tests in `e2e/tests/interrupted.spec.ts`:

| Case | What core fires | What Lens does |
|---|---|---|
| Exception thrown in a function (caught by the page or not) | `onFunctionException`. **No** `postFunctionInvoke`. | The function span is closed at the time of the exception event, marked interrupted and drawn striped. Its parent keeps its own end. |
| Exception thrown through an include and caught by a try/catch | `postTemplateInvoke` still fires. No exception event. | The include span closes normally. The exception is not recorded (use `lensException()`). |
| Uncaught exception | `onFunctionException` for the functions it passed, then `onError` and no `onRequestEnd`. | The request is finished from `onError`: open spans are closed and marked. Core renders its own error page, so there is no bar. |
| `abort` | `onAbort`, templates close normally. | The request is finished from `onAbort`. No bar. |
| Request timeout (`requestTimeout`) | Nothing. The web runtime did not interrupt the request in the harness. | The request is long, not interrupted. |
| Client disconnect | Nothing. The request runs to its end. | The request ends normally and keeps its full time. |
| Work in a thread that outlives the request | Nothing is attached to the request after it ended. | The thread adds no span, query or error to the finished request. |
| A request that never ends (a hung or killed thread, a lost event) | Nothing. | After `request.maxMinutes` (default 10) the watchdog finishes it as **unfinished**: spans closed and marked, kept in the history, removed from In flight, with the issue "Request never finished". |

Rules Lens follows:

- A span left open by a skipped `post` event ends at the time of the last exception event on its thread when that came after the span started, else at the last activity Lens saw in the request. It never gets the end time of the span around it.
- When a request finishes, `closeAll` marks every span that is still open as interrupted, with the same estimate.
- An interrupted span has `interrupted: true` in the payload. The bar and the console draw it striped with a marker, and its end is an estimate, not a measurement.

## Not in core

`onException`, `onSOAPRequest` and `onSOAPResponse` do not exist in core 1.19.

## Core gaps

Lens needs these core changes. Until they land, the matching features are limited. `postBIFInvocation` used to be on this list. It now exists in core with `elapsedNanos` and `onBIFException` (core pull request 657), and Lens uses it for the [BIFs panel](../panels/bifs.md). You need a BoxLang snapshot or release that has the change.

1. `onComponentInvocation` is declared but never announced.
2. There are no cache hit or miss events. Lens computes per-request cache numbers from the difference of cache statistics instead.
3. There are no SOAP events.
4. The web context does not announce `onRequestFlushBuffer`, so Lens cannot inject a bar into core's error pages.
5. Template events lack execution time. This is optional and cheap to add. Lens times templates itself.

## Lens interception points

Lens adds its own points for extension: `onLensRegister`, `onLensRequestStart`, `onLensCollect` and `onLensRequestFinish`. See [Extending Lens](../guides/extending.md#interception-points).
