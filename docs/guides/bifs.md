---
title: BIF Reference
order: 1
description: Built-in functions for talking to Lens from your code.
icon: lucide:code
---

# BIF Reference

These functions work whenever the module is loaded. When Lens is not collecting for the request, or the caller is not allowed, the first group does nothing. The [data functions](#data-functions) at the end work in every request, tracked or not.

| Function | Signature | Returns |
|---|---|---|
| [`lensMessage`](#lensmessage) | `( message, label="info" )` | nothing |
| [`lensStart`](#lensstart) | `( label )` | id |
| [`lensStop`](#lensstop) | `( labelOrId )` | nothing |
| [`lensMeasure`](#lensmeasure) | `( label, callable )` | the callable's result |
| [`lensAddMeasure`](#lensaddmeasure) | `( label, startMs, endMs )` | nothing |
| [`lensException`](#lensexception) | `( exception )` | nothing |
| [`lensEnable`](#lensenable-and-lensdisable) / [`lensDisable`](#lensenable-and-lensdisable) | `()` | nothing |
| [`lensIsEnabled`](#lensisenabled) | `()` | boolean |
| [`lensRender`](#lensrender) | `()` | string |
| [`lensDump`](#lensdump) | `( value, label="" )` | nothing |
| [`lensPanel`](#lenspanel) | `( id, label, renderer )` | a panel builder |
| [`lensReport`](#lensreport) | `()` | struct |
| [`lensErrors`](#lenserrors) | `( limit=20 )` | array of structs |
| [`lensQueries`](#lensqueries) | `( limit=20, sort="slowest" )` | array of structs |
| [`lensInflight`](#lensinflight) | `()` | array of structs |
| [`lensLicense`](#lenslicense) | `()` | struct |
| [`lensDiagnostics`](#lensdiagnostics) | `()` | struct |
| [`lensServer`](#lensserver) | `()` | struct |
| [`lensRequestId`](#lensrequestid) | `()` | string |

`lensConsole()` also exists. It serves the [console](../console/index.md) and is called only by the module's own `index.bxm`. Do not call it from your code.

## lensMessage

Adds a message to the [Messages](../panels/messages-and-timers.md#messages) panel. `label` can be `info`, `warn`, `error`, `debug` or any string. Structs and arrays are serialized.

```javascript
lensMessage( "Cache warmed" );
lensMessage( "Retrying payment", "warn" );
lensMessage( { orderId : 42, items : [ 1, 2, 3 ] }, "debug" );
```

## lensStart

Starts a timer and returns its id.

```javascript
id = lensStart( "import" );
```

## lensStop

Ends a timer. Pass the label or the id. Lens ignores labels that match no running timer.

```javascript
lensStart( "import" );
runImport();
lensStop( "import" );
```

## lensMeasure

Times a closure and returns its result.

```javascript
orders = lensMeasure( "load orders", () => {
	return queryExecute( "SELECT * FROM orders" );
} );
```

## lensAddMeasure

Adds a span you measured yourself. Pass start and end in milliseconds.

```javascript
start = getTickCount();
doWork();
lensAddMeasure( "doWork", start, getTickCount() );
```

## lensException

Records an exception you caught. See [Exceptions](../panels/exceptions.md).

```javascript
try {
	callPaymentApi();
} catch ( any e ) {
	lensException( e );
}
```

## lensEnable and lensDisable

Switch Lens on or off for the current request. The `access` rules still apply, so a blocked caller stays blocked.

```javascript
lensDisable(); // keep a noisy endpoint out of Lens
```

## lensIsEnabled

Returns true when Lens is active for the current request.

```javascript
if ( lensIsEnabled() ) {
	lensMessage( "Lens is on" );
}
```

## lensRender

Use it with `inject` set to `false` when you want to place the bar yourself. It returns a placeholder that Lens replaces with the bar when the request ends.

```javascript
writeOutput( lensRender() );
```

## lensDump

Pushes a value to the Messages panel as a collapsible tree. The optional label names it.

```javascript
lensDump( session.cart, "cart" );
```

## lensPanel

Creates a custom panel from app code. No module is needed. `renderer` is optional and defaults to `table`. The builder methods set the renderer for you. See [Extending Lens](extending.md#from-app-code).

```javascript
lensPanel( "jobs", "Jobs", "table" )
	.columns( [ "Job", "ms" ] )
	.rows( [ [ "email", 12 ], [ "report", 340 ] ] )
	.badge( 2, "none" );
```

Builder methods: `columns`, `rows`, `kv`, `tree`, `spans`, `messages`, `json`, `text`, `label`, `icon`, `order`, `badge( count, severity )` and `issue( severity, title, detail, file, line )`.

## Data functions

These five functions return the numbers the [console](../console/index.md) shows, as plain BoxLang structs and arrays. Use them to build a health endpoint, a smoke test or your own page. They work whether or not Lens tracks the current request, and they do not check `bar.access` or `console.access`. They describe every request on the server, so do not hand their output to untrusted users.

The harness page `harness/app/api/lens.json.bxm` calls all five and returns them as JSON. Read it for a working example.

Times are in milliseconds. Dates are milliseconds since the epoch.

### lensReport

`lensReport()` returns the data of the console [Reports](../console/errors-and-reports.md#reports) page.

| Key | Content |
|---|---|
| `startedAt`, `uptimeMs` | When this run started, and how long ago. |
| `persisted` | True when the disk store is on, so `lifetime` exists. |
| `session` | Totals since this start: `requests`, `errors`, `slow`, `queries`, `queryMs`, `httpCalls`, `exceptions`, `avgMs`, `maxMs`, `p50`, `p95`, `p99`, and `status` with the keys `1xx` to `5xx`. |
| `lifetime` | Only when `persisted` is true. Totals since first install: `since`, `runs`, `requests`, `errors`, `slow`, `queries`, `exceptions`. |
| `series` | One entry per minute: `t`, `requests`, `errors`, `avgMs`. |
| `seriesMinutes` | How many minutes the series keeps. |
| `slowestUrls`, `busiestUrls`, `failingUrls` | Up to 10 entries each: `url`, `count`, `errors`, `avgMs`, `maxMs`. |

```javascript
report = lensReport();
writeOutput( "p95: #report.session.p95# ms over #report.session.requests# requests" );
```

### lensErrors

`lensErrors( limit=20 )` returns the error groups of the console [Errors](../console/errors-and-reports.md#errors) page, newest first, at most `limit` (at least 1). Samples are not included. Each group is a struct:

| Key | Content |
|---|---|
| `id`, `type`, `message` | The group id, the error type and the message. |
| `count` | How many times it happened. |
| `firstSeen`, `lastSeen` | When. |
| `lastStatus` | The HTTP status of the last request. |
| `handled` | False for an uncaught error or an error status. |
| `file`, `line` | Where it was thrown, when known. |
| `urlCount`, `urls` | How many URLs hit it, and a struct of URL to count. |

```javascript
for ( group in lensErrors( 5 ) ) {
	writeOutput( "#group.count# x #group.type#: #group.message#<br>" );
}
```

### lensQueries

`lensQueries( limit=20, sort="slowest" )` returns the per statement statistics of the console [Queries](../console/in-flight-and-queries.md#queries) page, largest first, at most `limit` (at least 1).

| `sort` | Orders by |
|---|---|
| `slowest` | `maxMs`. Also used for any other value. |
| `total` | `totalMs` |
| `count` | `count` |
| `failures` | `failures` |

Each row has `sql`, `datasource`, `count`, `failures`, `slow`, `avgMs`, `maxMs`, `minMs`, `totalMs`, `rows`, `firstSeen`, `lastSeen`, `file`, `line`, `lastError`, `slowestRequest`, `lastRequest` and `serverId`. The SQL has placeholders, never parameter values.

```javascript
top = lensQueries( 3, "total" );
writeOutput( top[ 1 ].sql & " took " & top[ 1 ].totalMs & " ms in total" );
```

### lensInflight

`lensInflight()` returns the requests Lens tracks that are running now, longest first. Each entry has `id`, `method`, `uri`, `queryString`, `app`, `remoteAddr`, `serverHost`, `serverIp`, `serverId`, `startedAt`, `elapsedMs`, `thread`, `threadState` and `queries` (the number run so far). It is an empty array when nothing is running.

```javascript
stuck = lensInflight().filter( ( r ) => r.elapsedMs > 5000 );
```

### lensLicense

`lensLicense()` returns the license state, the same one the console header shows. See [Licensing](../licensing.md#license-states).

| Key | Content |
|---|---|
| `state` | `plus`, `trial`, `expired` or `none`. `none` is the Free state. |
| `label` | The text the console shows, for example `BoxLang+ active` or `Free`. |
| `daysLeft` | The days left of a trial. `null` in every other state. |
| `note` | A message from the license check, often empty. |
| `plansUrl` | The BoxLang+ plans page. |

```javascript
if ( lensLicense().state == "expired" ) {
	lensMessage( "The BoxLang+ license has expired", "warn" );
}
```

### lensDiagnostics

`lensDiagnostics()` shows how Lens itself is doing: `version`, `enabled`, `collectLevel`, `history` (`size`, `capacity`) and `server` (the identity of this server) and `async`, the state of the [work queue](production.md#work-off-the-request-thread): `enabled`, `depth` (waiting), `capacity`, `processed`, `dropped` (refused because the queue was full) and `failed` (tasks that threw).

### lensServer

`lensServer()` returns the identity of this server, the one every request, error, report, statistic and audit line carries: `host`, `ip` (the primary address), `addresses` (all usable addresses), `id` (the id of this instance, stable for one JVM start) and `runtime` (the BoxLang runtime instance name, empty when there is none). It is detected once, not on every call. See [`server`](../configuration.md#server).

```javascript
writeOutput( "Served by " & lensServer().host & " (" & lensServer().id & ")" );
```

The arrays of `lensErrors()`, `lensQueries()` and `lensInflight()` keep their shape, so each row carries its own `serverId` (and `serverHost` and `serverIp` for in flight requests) instead of a wrapper. `lensReport()` and `lensDiagnostics()` have a `server` key.

### lensRequestId

`lensRequestId()` returns the id Lens gave this request, the same one as the `X-BxLens-Id` response header, `request.bxlens.id`, the id in the bar and the console and the logging context key `requestId`. It is an empty string when the request is not tracked. Use it to tie your own log lines or a support ticket to the request.

```javascript
writeLog( text = "order saved", log = "orders", type = "info" );   // with %X{requestId} in the log pattern, the id is on the line
return { ok : true, requestId : lensRequestId() };
```

## Not available

These are not implemented in v1: `lensDumpHeap`, `lensThreadDump` and the `bx:lens` component. Use the BIFs above in templates inside `<bx:script>` blocks.
