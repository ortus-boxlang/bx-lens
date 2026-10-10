---
title: Lensy tools
order: 3
description: Every tool Lensy can use, who may use it and what it does.
icon: lucide:wrench
---

# Lensy tools

The [Lensy](../console/ai.md) answers by calling these tools. A tool is either **LOOK** (it reads what the console already shows) or **ACT** (it changes something and waits for your Approve click). The **Role** column says who may use it: `viewer and admin` means both console roles, `admin` means only the admin role. A viewer is not even offered the admin tools, and the server refuses them if the model asks anyway.

Every call, whoever makes it, goes through one gate (the Toolbox): the license is checked (BoxLang+), the role, the read only mode and `console.actions`, the arguments are checked against the tool, the result is stripped of secrets and cut to 12,000 characters, and an `ai.tool` line is written to the [audit log](../security.md#audit-log). See the [threat model](../security.md#lensy-threat-model).

No tool takes SQL text. The database tools read metadata only. There is no tool to restart or stop the server, to take a heap dump or to read a file.

## Inspect and runtime

| Tool | What it does | Role | Kind | Arguments |
|---|---|---|---|---|
| `overview` | Traffic and error numbers of this server: requests in memory and per second, how many returned a 5xx, the median, p95 and p99 time, status code classes, the slowest routes, the busiest, slowest and most failing URLs. Use it for 'how many requests did we serve' and 'what is the error rate'. | viewer and admin | LOOK | none |
| `requests` | List recent requests, newest first, each with id, method, URL, status, time in ms, number of SQL queries and the number of issues Lens found. Use problemsOnly to see only the failed, slow or flagged ones. | viewer and admin | LOOK | `limit`?, `problemsOnly`?, `urlContains`? |
| `requestDetail` | One request in detail: its issues (N+1 queries, slow queries, slow templates, failed HTTP calls), the slowest SQL statements, exceptions and outgoing HTTP calls. Get the id from requests. | viewer and admin | LOOK | `id` |
| `inFlight` | The requests running right now, longest first, with the thread each one runs on. Use it to find a request that is stuck or slow right now. | viewer and admin | LOOK | none |
| `errors` | Errors grouped by cause, newest first, with counts and where they happen. Pass an id from the list to get the latest samples with the stack of one group. | viewer and admin | LOOK | `limit`?, `id`? |
| `queryStats` | SQL statements across all requests, ranked: runs, failures, slow runs, average, maximum and total time. Statements have placeholders and never parameter values. Use it to find slow or failing queries. | viewer and admin | LOOK | `limit`?, `sort`? |
| `executors` | The thread pools (executors) of the runtime with utilization, queue size and capacity, the runtime's own health report, and findings with concrete recommendations such as a new pool size or queue capacity. Use it to answer 'how healthy are the executors and how do I improve them'. | viewer and admin | LOOK | `onlyProblems`? |
| `tasks` | The scheduled tasks and their schedulers: status, schedule, last and next run, runs and failures. | viewer and admin | LOOK | none |
| `datasources` | The database connection pools: active, idle and waiting connections, maximum size, timeouts, and findings when a pool is saturated. No URLs and no user names. | viewer and admin | LOOK | none |
| `caches` | The caches: names, provider, object count, hits, misses, hit rate, evictions and size limits. Statistics only, never the values. | viewer and admin | LOOK | none |
| `modules` | The loaded BoxLang modules with their versions, and the Lens integrations (for example ORM) with their status. | viewer and admin | LOOK | none |
| `configuration` | The effective BoxLang runtime configuration by area, secrets hidden. Pass filter to find a setting by name or value. | viewer and admin | LOOK | `filter`? |
| `lensInfo` | Facts about Lens itself and this server: the server identity (host name, address, id), the Lens version, collect level, the license state, the Plus features that are on and the live Lens settings. | viewer and admin | LOOK | none |
| `searchDocs` | Search the Lens documentation and return the best matching sections with their page names. Use it for questions about what a page or setting does and how to configure Lens. | viewer and admin | LOOK | `question` |

## JVM introspection

| Tool | What it does | Role | Kind | Arguments |
|---|---|---|---|---|
| `diagnose` | A health sweep that checks threads, executors, heap and garbage collection, datasources, error rate, slow requests and running requests, and returns findings sorted by severity with the evidence and the next step. Start here for 'is the server healthy' and 'what is slow right now'. A viewer gets the parts a viewer may see. | viewer and admin | LOOK | none |
| `system` | JVM and host numbers: CPU, heap and non-heap memory, memory pools, garbage collectors, class loading, thread counts, disks, uptime and JVM flags. Admin only. | admin | LOOK | none |
| `threadsSummary` | Thread counts by state (RUNNABLE, BLOCKED, WAITING, TIMED_WAITING), how many are blocked on a monitor, deadlocked threads, the largest thread pools and findings. Use it for 'do we have any blocked threads'. Admin only. | admin | LOOK | none |
| `blockedThreads` | Threads that are blocked or waiting on a lock, with the lock each one waits for, who holds it and the top of the stack, and deadlock detection: the threads in a deadlock with their locks and owners. Admin only. | admin | LOOK | none |
| `topCpuThreads` | The threads that used the most CPU time since they started, with their pool and top frames. Call it twice a few seconds apart to see which one is burning CPU now. Admin only. | admin | LOOK | `limit`? |
| `threadPools` | Threads grouped by pool name (the thread name without its number) with the count per state. Use it to see which pool has too many threads or is stuck. Admin only. | admin | LOOK | none |
| `threadStack` | The stack of one thread by name, with the locks it holds and the lock it waits for. Get names from blockedThreads, threadPools or inFlight. Admin only. | admin | LOOK | `name` |
| `gcPressure` | Garbage collection pressure: heap use, heap after the last collection, the share of time spent collecting and findings. Admin only. | admin | LOOK | none |

## Logs and environment

| Tool | What it does | Role | Kind | Arguments |
|---|---|---|---|---|
| `logs` | Read the server logs. Without a file it lists the log files. With a file it returns the latest lines, optionally those that contain a text or a level. Secrets are hidden. Admin only. | admin | LOOK | `file`?, `query`?, `lines`?, `level`? |
| `environment` | The environment of the runtime: Java and OS facts, JVM arguments, environment variables and system properties, with secret values hidden. Admin only. | admin | LOOK | none |

## Database metadata

| Tool | What it does | Role | Kind | Arguments |
|---|---|---|---|---|
| `dbTables` | List the tables and views of a datasource from the JDBC metadata, with an approximate row count where the driver reports one. No SQL is run. Admin only. | admin | LOOK | `datasource`, `filter`? |
| `dbColumns` | List the columns of one table (name, type, size, nullable) and its primary key from the JDBC metadata. No SQL is run. Admin only. | admin | LOOK | `datasource`, `table` |
| `dbTest` | Open a connection to a datasource, validate it and report how long that took. Admin only. | admin | LOOK | `datasource` |

## Actions (each needs your approval)

| Tool | What it does | Role | Kind | Arguments |
|---|---|---|---|---|
| `taskAction` | CHANGES THE SERVER. Run now, pause, resume or reload a scheduled task. The administrator must approve it first. Use it only when the person asked for it. | admin | ACT | `scheduler`, `task`?, `action` |
| `cacheAction` | CHANGES THE SERVER. Evict one key, reap expired objects or clear a whole cache. The administrator must approve it first. Use it only when the person asked for it. | admin | ACT | `cache`, `action`, `key`? |
| `runGc` | CHANGES THE SERVER. Run a garbage collection now. It can pause the application for a moment. The administrator must approve it first. | admin | ACT | none |
| `setIntegration` | CHANGES THE SERVER. Turn a Lens integration (for example orm) on or off. The administrator must approve it first. | admin | ACT | `id`, `enabled` |
| `changeSetting` | CHANGES THE SERVER. Change one live Lens setting, for example thresholds.slowQueryMs. Settings about AI, access, the console and the disk store cannot be changed. The administrator must approve it first. | admin | ACT | `key`, `value` |

An argument with a question mark is optional. `diagnose` is open to the viewer role, but a viewer gets only the parts of the sweep a viewer may see (executors, datasources, error rate, slow and running requests) and the answer says what was left out.

## MCP tools

Tools of the [MCP servers](../console/ai.md#servers-lensy-can-ask-mcp) the admin turned on are added to the list above. They are not in the catalog: they are found on each server (`tools/list`, 10 seconds, kept for 5 minutes) and filtered by the list the admin ticked.

| Item | Rule |
|---|---|
| Name | `server.tool` for people (chips, approvals, audit), `server__tool` for the model. A character in a tool name other than a letter, digit, underscore or hyphen becomes `_`, and a name is cut to 64 characters. |
| Description | `[Server name] description`, cut to 500 characters. |
| Arguments | The schema the server published, reduced to properties with a simple type (text, number, whole number, true or false), a short description and the required names. Anything else is dropped. The call is checked against it: required arguments, types, 2000 characters at most, no control characters, names the schema does not have are dropped. |
| Role | Admin: every enabled server. Viewer: only the built-in documentation servers. |
| Approval | Built-in servers: none (except `sendFeedback`). Custom servers: your click every time, unless the server is marked trusted, read only. A call that needs a click is refused when `console.readOnly` is on, or `console.actions` or `ai.actions` is off. |
| Gate | The same Toolbox as every other tool: BoxLang+, role, enabled and ticked, arguments, tool calls per answer (`ai.maxToolCalls`), 120 calls a minute, redaction and the 12,000 character cap. The result is wrapped with a reminder that it is data from outside. |
| Audit | `ai.mcp server=... tool=... result=ok|denied|error ms=...` |

## Settings the agent cannot change

`changeSetting` can change a live Lens setting, for example `thresholds.slowQueryMs`. It can never change `ai.*` (a changed address or key would send your data elsewhere), `console.*`, `access.*`, `bar.access` or `store.*`, even when you approve.

## Adding a tool

A new tool goes through the Toolbox, is audited and role checked. Add it in three places and a test checks that they agree: the method with `@AITool` in `models/ops/LensyTools.bx` (its comment is what the model reads), the entry in `ops/Tools.java` (name, role, kind, arguments) and a row in this table. An ACT tool must also be admin only and needs an approval.
