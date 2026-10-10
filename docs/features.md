---
title: Features
order: 2
description: Everything BX Lens does today, for the debug bar and the console, and what is Free or BoxLang+.
icon: lucide:list-checks
---

# Features

BX Lens has two parts. The **bar** is a snapshot of one request, shown to developers on the page itself. The **console** is a password protected page for one running server. Clusters, long history and alerting belong to BX Insights, not here.

**Free** means no BoxLang+ subscription is needed. **Plus** means a BoxLang+ license or trial. The split is the current state and may change, see [Licensing](licensing.md).

## The debug bar

The bar is off by default and only shows to allowed callers (loopback by default). It says what happened in this request and nothing more. Analysis lives in the console.

| Feature | What it does | Tier |
|---|---|---|
| Strip | Status, total time, query count and time, exception count, request id, a Console button, theme toggle. Red only for a 5xx or an uncaught exception | Free |
| Timeline | One waterfall of templates, functions (opt in), queries, HTTP calls and transactions, nested the way they ran. Zoom, pan, filter, search with `/`, click a span for detail and an open in editor link | Free |
| Interrupted spans | A span whose closing event never fired ends at the exception (or last activity) and is marked interrupted, with its own marker | Free |
| Queries | Every statement with SQL, rows, time, datasource and the line that ran it. Bound values only when `includeParams` is on. ORM statements carry an ORM label | Free |
| Exceptions | Caught and uncaught, with BoxLang location and the Java stack. The panel can open itself on a caught exception | Free |
| HTTP | Outgoing HTTP calls with status, size and time | Free |
| Messages and Timers | What your code sends with `lensMessage()`, `lensDump()`, `lensMeasure()`, `lensStart()` and `lensStop()` | Free |
| Request | Request and response headers, status and the request cost | Free, cost is Plus |
| Request cost | CPU time and memory allocated by the request thread | Plus |
| Scopes | Redacted copy of url and form (others opt in). Off by default because it costs time | Free |
| BIFs | Calls, total, average and slowest time and errors per built in function. Off by default | Free |
| Runtime | A cached snapshot: BoxLang and Java versions, OS, uptime, heap, server and app name, cache names, and the main `boxlang.json` settings. Secrets hidden | Free |
| Your panels | Applications and modules add panels with `lensPanel()` or the `onLensCollect` interception point | Free |
| Bar designer | Show, hide and reorder the bar tabs from the console | Look Free, save Plus |
| Request id | Every tracked request gets a short id, sent as `X-BxLens-Id`, shown in the bar and the console, and set as `request.bxlens.id` and `lensRequestId()` | Free |

How the bar stays light: the page gets a block of about 1 KB. The CSS, JavaScript and icons are separate files, hashed and cached by the browser. The bar data is built only when the page renders for an allowed caller.

## The console

One page at `/~bxlens/index.bxm`, with its own password, an optional second viewer password, lockout after failed logins, CSRF tokens and a strict content security policy. It makes no request to any other site.

### Inspect

| Page | What it does | Tier |
|---|---|---|
| Overview | Requests, errors, p95 and what needs attention | Free |
| Requests | Every request in memory, including JSON and SSE. Click one for its timeline, issues (N+1, slow query, slow template, failed HTTP, security notes), headers and cost. Copy as JSON or cURL. Unfinished and interrupted requests are marked | Free, last 25 kept. Plus keeps the configured size |
| In flight | Requests running now, longest first, with a live stack of what each one is doing | Free |
| Errors | Errors grouped by cause with counts, where, and a stack and request context per sample. Redacted prompt copy for an AI chat | Free in memory, saved to disk with Plus |
| Reports | Totals, error rate, p95 and p99, status codes, busiest and slowest URLs, a minute series | Free for 60 minutes, longer and saved with Plus |
| Queries | Statements ranked by slowest, total time, count and failures, across every request | Free |
| Ask Lens | Ask about this server in plain words. Copy prompt, Ask ChatGPT and Ask Claude buttons are free. With BoxLang+ it is the full page of the [Lensy](console/ai.md) | Free copy, Plus assistant |

### Runtime

| Page | What it does | Tier |
|---|---|---|
| Executors | Pool, thread and queue use against thresholds, a 60 second chart, health notes, and the tasks bound to each scheduler executor | Free |
| Tasks | Scheduled tasks with status, schedule, next and last run, runs and failures. Run now, pause, resume, reload | View Free, actions Plus |
| Datasources | Pool size, active and idle connections, waits and timeouts. Test connection | Free |
| ORM | Opt in integration with bx-orm. Event totals, flushes, failures and Hibernate statistics, with a switch to turn statistics on | Page is Plus, the ORM SQL in Queries is Free |
| Caches | Hit rate and counts per cache, a capped key list. Read a value, evict, reap, clear | Statistics Free, actions Plus |
| Logs | Every file in the logs directory. Search, level filter, live tail | Free (admin), download Plus |
| Modules | Loaded modules, versions and settings, plus the Integrations section with a switch per integration | Free |
| Environment | The effective configuration, JVM arguments, variables and properties, secrets hidden. Diagnostic bundle | Free (admin), bundle Plus |
| Configuration | The effective BoxLang settings by area with the value and its source (default, `boxlang.json` or environment variable), and a filter box | Free |
| System | CPU, heap, memory pools, GC, disks and runtime details. Run GC. Deadlock detection. Heap dump | Free, heap dump Plus and off until `console.allowHeapDump` |
| Threads | Every thread with state, CPU and stack. BoxLang frames highlighted. Copy or download a thread dump | Free (admin) |

### Config

| Page | What it does | Tier |
|---|---|---|
| Bar designer | Choose and order the bar tabs | Save Plus |
| AI | Status, requirements, live settings and a connection test for Lensy. The key is never typed in: an environment variable name or a `bxsecret:` value | View Free, change Plus |
| Settings | Change thresholds, collectors, interface and limits live, with a reset, saved to a small overrides file. Passwords, access rules, `console.*`, `store.*` and the AI key stay in `boxlang.json` | Free |

### Lensy

A chat in a drawer on every console page (and the full page of Ask Lens) that answers by calling tools: overview, requests, errors, queries, executors with concrete pool recommendations, threads, blocked threads and deadlocks, garbage collection pressure, datasources, logs, database metadata and a `diagnose` health sweep, plus a search of these docs. Actions (tasks, caches, run GC, integrations, a live setting) wait for an Approve click. Default model: Ollama on this machine. BoxLang+. See [the AI page](console/ai.md), [the tool list](reference/ai-tools.md) and the [threat model](security.md#ops-assistant-threat-model).

### Roles and control

- **Admin** and **viewer** roles. A viewer sees overview, requests, in flight, queries, errors, reports, executors, tasks, datasources, cache statistics and modules. Logs, thread stacks, environment, system details and a few sensitive setting values are admin only.
- **Read only mode** refuses every change from the console.
- **Audit log** (`bxlens-audit.log`) records every change, download and denied attempt. No secrets.
- Access rules per surface: loopback, an IP list, allowed hosts (DNS rebinding guard), a required header, and proxy header trust only from trusted peers.

## Server identity

Every record says which machine produced it, so the data of many servers can end up in one database and still be told apart. This is built in and free. It is a name and an address, never a lookup while a request runs.

| What | Where the identity appears |
|---|---|
| A request | The `serverHost`, `serverIp` and `serverId` of the summary and the detail, the Request tab of the bar, the console request detail, the Requests table (a Server column shows when the history holds more than one server id), the JSON and cURL exports |
| Errors | The group and every sample, and a top level `server` on the list |
| Reports and queries | A top level `server` on the totals and on the query statistics, and the server id of each statement |
| In flight | Each running request |
| Audit log | Every line has `server=<id>` |
| Disk store | `errors.json` and `reports.json` have a top level `server`, every error group and sample has its own `serverId` |
| BIFs | `lensServer()`, and the `server` key of `lensReport()` and `lensDiagnostics()`. The rows of `lensErrors()`, `lensQueries()` and `lensInflight()` carry `serverId` |
| Response header | `X-BxLens-Server`, only next to `X-BxLens-Id` and only with `history.serverHeader` |
| Console | The host name and address in the header and on the Overview |

The identity is detected once at start and looked at again every five minutes. Set `server.name`, `server.address` and `server.id` (or the environment variables `LENS_SERVER_NAME`, `LENS_SERVER_ADDRESS` and `LENS_SERVER_ID`) behind containers or NAT. See [`server`](configuration.md#server).

## Integrations

An integration reads another module's events. It is **off by default**, and it is only offered when that module is installed. Until both are true Lens registers no listener.

| Integration | Needs | What you get |
|---|---|---|
| ORM | bx-orm 1.7.2 or later, `collectors.orm.enabled` | ORM SQL in the bar and console queries with an ORM label, flush counts, failed statements, startup DDL, and (Plus) the statistics page |

How to add one: [Integrations](reference/integrations.md).

## BIFs for your code

`lensMessage()`, `lensDump()`, `lensMeasure()`, `lensStart()`, `lensStop()`, `lensException()`, `lensPanel()`, `lensEnable()`, `lensDisable()`, `lensIsEnabled()`, `lensRender()`, `lensRequestId()`, and for tools and dashboards `lensReport()`, `lensErrors()`, `lensQueries()`, `lensInflight()`, `lensLicense()`, `lensDiagnostics()` and `lensServer()`. The tracking BIFs do nothing when the request is not tracked.

## Safe by default

| Setting | Default | Why |
|---|---|---|
| `bar.enabled`, `console.enabled` | off | Nothing runs until you ask |
| `bar.access`, `console.access` | local | Loopback only |
| `collect.level` | `light` | No bound values, scopes, request headers, caller lookups or stack frames beyond what is cheap |
| `collectors.queries.includeParams` | off | Values can be personal data |
| `collectors.functions`, `bifs`, `logs`, `scopes`, `orm` | off | They cost time or copy data |
| `console.allowHeapDump` | off | A dump holds every secret in memory |
| `ai.enabled` | off | When on, prompts and what the assistant reads leave the server (a local model keeps them in your network) |

- Redaction happens before storing. Statement text is stored with literals masked, never parameter values.
- Every in-memory store has a hard cap and drops the oldest entry.
- A tracked request that never finishes is closed after `request.maxMinutes` (10) and kept as unfinished.
- Heavy aggregation (query statistics, error groups, reports, history, audit) runs on one bounded background worker. When its queue is full new work is dropped and counted, and a request never waits.

## Speed

Measured on `/index.bxm` with the MiniServer and 8 clients (requests per second): installed with bar and console off about 2770, console on at `light` about 2190, bar shown about 1650. The full table is in [Performance](project/performance.md).

## What it does not do

- Clusters, shared history and alerting: use BX Insights.
- Capture bound query values by default.
- Add a request id to outgoing HTTP calls. `collectors.http.propagateId` is reserved: core gives Lens a read only HTTP request, so it cannot add a header yet.
- Record request timeouts or client disconnects. Core does not announce them, so only the unfinished request sweep covers those.
