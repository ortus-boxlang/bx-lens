---
title: Core Event Inventory
order: 1
description: The BoxLang core 1.19 events Lens listens to and the payload keys each one sends.
icon: lucide:radio
---

# Core Event Inventory

This page is for module authors. It lists the BoxLang core 1.19 events that Lens uses, with the payload keys core actually sends. The data comes from the [design spec](../design/api-spec.md), which read them from the core source.

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
| `onBIFInvocation` | `context, arguments, bif, name` (+ `result` on the second call) | BIF timing. See the bug note below. |
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

These are not per request. They feed a rolling global activity view in the Lens service: scheduled tasks, executors and watchers.

`onSchedulerStartup`, `onSchedulerShutdown`, `onSchedulerRestart`, `schedulerBeforeAnyTask`, `schedulerAfterAnyTask`, `schedulerOnAnyTaskSuccess`, `schedulerOnAnyTaskError`, `onSchedulerRegistration`, `onSchedulerRemoval`, `onAllSchedulersStarted`, `onWatcher*`, module events (`preModuleLoad`, `postModuleLoad` and others), `onRuntimeStart`, `onRuntimeShutdown`, `onConfigurationLoad`.

## Not in core

`onException`, `onSOAPRequest` and `onSOAPResponse` do not exist in core 1.19.

## Core gaps

Lens needs these core changes. Until they land, the matching features are limited.

1. `postBIFInvocation` is never announced. The post call re-announces `onBIFInvocation`, so Lens detects the call that carries `result`.
2. `onComponentInvocation` is never announced.
3. There is no cache read, hit or miss event.
4. There are no SOAP events.
5. Template events lack execution time. This is optional and cheap to add.

## Lens interception points

Lens adds its own points for extension. See [Extending Lens](../guides/extending.md#interception-points).
