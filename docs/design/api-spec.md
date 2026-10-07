# BX Lens: API and Shape Spec (DRAFT for approval)

Request-level debug bar for BoxLang web apps (MiniServer, servlet, CommandBox). In-memory only. HTML responses only. Open source. Observability across requests is out of scope (that is BX Insights).

## Status (v1 as implemented)

This page is the original design draft. Where it disagrees with the shipped module, the user docs win: see [Configuration](../configuration.md), [BIF Reference](../guides/bifs.md), [Extending Lens](../guides/extending.md) and [Core Event Inventory](../reference/events.md).

**Implemented in v1**

- Collectors: templates, functions (opt in), queries, HTTP, exceptions, messages, timers, transactions, logs (opt in), scopes, JVM (Runtime), cache, modules, plus request lifecycle data.
- Panels: Issues, Timeline, Queries, Templates, HTTP, Exceptions, Messages, Timers, Cache, Modules, Request, Scopes, Runtime and History.
- BIFs: `lensMessage`, `lensDump`, `lensStart`, `lensStop`, `lensMeasure`, `lensAddMeasure`, `lensException`, `lensEnable`, `lensDisable`, `lensIsEnabled`, `lensRender`, `lensPanel`.
- Tier 1 extension: `onLensRegister`, `onLensRequestStart`, `onLensCollect`, `onLensRequestFinish`, with the table, kv, tree, spans, messages, json and text renderers.
- Editor links with `editor.remoteBase` and `editor.localBase` path mapping.
- In-memory History ring buffer (default 50) with JSON, SSE and other non-HTML requests.

**Changed from this draft**

- The settings are exactly the keys in `ModuleConfig.bx`. There is no `injectPosition`, no `X-BxLens` cookie or header override, no `collectors.request`, `timeline` or `session`, and slow thresholds live only under `thresholds`. Added: `limits`, `collectors.transactions`, `collectors.logs.max`, `collectors.queries.captureCaller`, `collectors.scopes.variables` and `dev.reloadAssets`. `collectors.cache` is on by default.
- There is no `/~bxlens/` asset endpoint. Lens inlines CSS, JavaScript and data into the HTML response, about 110 KB each.
- Detach opens a floating window inside the page with a Dock button, not a separate browser window.
- History shows summaries only. Loading the detail of an earlier request is not available.
- Uncaught exceptions and aborts skip `onRequestEnd`, and the web context does not announce `onRequestFlushBuffer`, so no bar is injected on core's error page. The request is still recorded in History.
- The Cache panel computes per-request numbers from the difference of cache statistics, because core announces no cache read events.
- Redaction matches any key that contains a redact key, ignoring case.
- The JVM panel is named Runtime.

**Planned, not implemented**

- `lensDumpHeap`, `lensThreadDump`, `allowHeapDump`, `collectors.jvm.threadDump`.
- The `bx:lens` component.
- Tier 2 (Java collectors) and Tier 3 (custom UI, `ui.allowCustomPanels`).
- Compare two requests.
- BIF call collector, components panel and SOAP events, which need core changes (section 3.7).

## 1. Architecture

| Piece | Responsibility |
|---|---|
| `LensService` (global service) | Owns config, collector registry, `RequestStore`, `GlobalStats`. |
| `LensRequest` | Typed per-request data. Stored as a context attachment on the request context. |
| `ILensCollector` | SPI. One class per panel. Java only. Registered as interceptors by the service. |
| `RequestStore` | Bounded ring buffer of finished `LensRequest` snapshots (default 50). No disk, no DB. |
| `LensInjector` | Interceptor that renders the bar into HTML responses at end of request. |
| Asset endpoint | Planned in this draft, not implemented. v1 inlines the assets in the page instead. |

Request flow: `onRequestStart` creates `LensRequest` (if allowed) then collectors fill it then `onRequestEnd` / `onError` / `onAbort` finalize, store, and inject.

## 2. Collector SPI

```java
public interface ILensCollector {
    String id();                       // "queries"
    String label();                    // "Queries"
    String icon();                     // icon key used by the UI
    int    order();                    // tab order
    void   configure( LensConfig cfg );// called once, cfg is immutable and cached
    void   onRequestStart( LensRequest req );   // optional setup, default no-op
    void   onRequestFinish( LensRequest req );  // optional snapshot work, default no-op
    Object payload( LensRequest req ); // JSON-safe: records, maps, lists, numbers, strings
    Badge  badge( LensRequest req );   // count + severity for the tab (NONE, WARN, CRIT)
}
```

Rules:
- Collectors never throw into a request. Failures are logged to the `bxLens` logger and the collector is skipped for that request.
- Per-request state lives in `LensRequest.slot( id )`, never in collector fields (collectors are shared across threads).
- Hot paths use typed records and preallocated bounded buffers. No `Map<String,Object>` per event.
- Time uses `System.nanoTime()` offsets from request start.
- Third parties can add collectors via `LensService.register( ILensCollector )` from their own module.

## 3. Event inventory (BoxLang core 1.19, read from source)

Payload keys below are what core actually sends. The current collectors read keys that do not exist (for example `params`/`recordCount` on `postQueryExecute`, `bodyLength` on `onHTTPRequest`), so they must be rewritten against this table.

### 3.1 Request lifecycle (announced globally and per app with `Key.*`)
| Event | Payload | Use |
|---|---|---|
| `onRequestStart` | `context, args, application, listener` | create `LensRequest`, access check |
| `onRequest` | same | template/class entry |
| `onRequestEnd` | same | finalize, store, inject. **Skipped on unhandled exceptions and aborts** (`WebRequestExecutor`) |
| `onError` | same (`args` holds the exception) | exception capture, finalize and inject on error pages |
| `onAbort` | same | finalize and inject on abort |
| `onMissingTemplate` | same | 404 issue |
| `onSessionStart`, `onSessionEnd` | same | session panel |
| `onRequestFlushBuffer` | `context, output` (mutable) | alternative injection point, fires on every flush so only the last flush may be modified |
| `onApplicationStart/End/Restart/Defined`, `beforeApplicationListenerLoad`, `afterApplicationListenerLoad` | app data | App panel |
| `onWebExecutorRequest` (web-support) | `context, appListener, requestString, exchange, updatedRequest` | route rewrites, request identity |

### 3.2 Execution
| Event | Payload | Use |
|---|---|---|
| `preTemplateInvoke`, `postTemplateInvoke` | `context, template, templatePath` | templates/includes tree. No `executionTime`, we time it ourselves |
| `preFunctionInvoke`, `postFunctionInvoke` | `context, arguments, function, name` (+ `result` on post) | functions panel (opt in, hot path) |
| `onFunctionException` | same + `exception` | exceptions |
| `onBIFInvocation` | `context, arguments, bif, name` (+ `result` on 2nd call) | BIF panel. **Core bug**: the post-call re-announces `onBIFInvocation` instead of `postBIFInvocation`, so the post event never fires. Workaround: detect the call carrying `result`. File a Jira |
| `onPreSourceInvoke`, `onPostSourceInvoke` | script/source execution | eval/script timing |
| `afterBoxClassCreation`, `afterBoxClassInit`, `afterDynamicObjectCreation`, `onCreateObjectRequest` | object created | Objects panel (opt in) |
| `onComponentInstance`, `onBIFInstance` | descriptor | startup only |
| `onComponentInvocation` | declared in `BoxEvent`, **never announced by core** | cannot be used today. Needs a core change for a Components panel |

### 3.3 Data
| Event | Payload | Use |
|---|---|---|
| `onQueryBuild` | query build data | builder usage |
| `preQueryExecute` | `sql, bindings, pendingQuery, context` | start time, SQL text |
| `postQueryExecute` | `sql, bindings, executionTime, data, result (meta), pendingQuery, executedQuery, context` | queries panel; row count from `data`, datasource from `pendingQuery` |
| `queryAddRow` | query row | ignore |
| `onTransactionBegin/Acquire/Commit/Rollback/SetSavepoint/Release/End` | `connection, transaction, context` | transactions panel, shown as spans on the waterfall |
| `onDatasourceConfigLoad`, `onDatasourceStartup`, `onDatasourceInitialized`, service start/stop | datasource | JVM/Runtime panel |
| `afterCacheElementInsert/Updated/Removed`, `beforeCacheElementRemoved`, `afterCacheClearAll` | cache element | cache writes only. **No read/hit/miss event** |
| `onCacheComponentAction` | cache component action | output cache usage |

### 3.4 I/O and misc
| Event | Payload | Use |
|---|---|---|
| `onHTTPRequest` | `result, httpClient, httpRequest` | HTTP panel start |
| `onHTTPRawResponse`, `onHTTPResponse` | `result, response, httpClient, httpRequest` | status, size, time |
| `onHTTPError` | error data | issue |
| `onFileComponentAction` | file action | Files panel (opt in) |
| `logMessage` | `text, log, type` | Logs panel |
| `onBXDump` / `onMissingDumpOutput` | dump data | route `bx:dump` output into Messages instead of the page body |
| `beforeObjectMarshallSerialize` etc. | serialization | ignore |

### 3.5 Global, not per request (Runtime/Admin panels)
`onSchedulerStartup/Shutdown/Restart`, `schedulerBeforeAnyTask`, `schedulerAfterAnyTask`, `schedulerOnAnyTaskSuccess`, `schedulerOnAnyTaskError`, `onSchedulerRegistration/Removal`, `onAllSchedulersStarted`, `onWatcher*`, module events (`preModuleLoad`, `postModuleLoad`, ...), `onRuntimeStart`, `onRuntimeShutdown`, `onConfigurationLoad`. These feed a rolling global activity view (scheduled tasks, executors, watchers) kept in `LensService`, not in `LensRequest`.

### 3.6 Removed from the current code
`onException`, `onSOAPRequest`, `onSOAPResponse` do not exist in core.

### 3.7 Core gaps to request
1. `postBIFInvocation` is never announced (bug).
2. `onComponentInvocation` is never announced.
3. No cache read/hit/miss event.
4. No SOAP events.
5. Template events lack execution time (cheap to add, optional).

## 4. Settings (`boxlang.json` > modules > bxLens > settings)

```json
{
  "enabled": false,
  "access": {
    "allowedIPs": [ "127.0.0.1", "::1" ],
    "allowPrivateNetworks": true,
    "allowedHosts": [],
    "requireHeader": ""
  },
  "inject": true,
  "injectPosition": "bodyEnd",
  "contentTypes": [ "text/html" ],
  "excludePaths": [ "/~bxlens/*", "/favicon.ico" ],
  "history": { "trackNonHtml": true, "header": "X-BxLens-Id", "maxRequests": 50 },
  "ui": { "theme": "auto", "startOpen": false, "autoOpenOnException": true, "defaultTab": "timeline", "height": 360, "allowDetach": true, "hotkey": "Ctrl+`" },
  "thresholds": { "slowRequestMs": 500, "slowQueryMs": 25, "slowTemplateMs": 100, "nPlusOneMin": 3 },
  "editor": { "linkPattern": "vscode://file/{path}:{line}", "remoteBase": "", "localBase": "" },
  "redact": { "keys": [ "password", "pwd", "token", "secret", "apikey", "authorization", "cookie" ], "mask": "[redacted]" },
  "collectors": {
    "request":    { "enabled": true },
    "timeline":   { "enabled": true },
    "templates":  { "enabled": true, "max": 300 },
    "functions":  { "enabled": false, "max": 1000, "minMs": 0 },
    "queries":    { "enabled": true, "max": 200, "slowMs": 25, "detectDuplicates": true, "detectNPlusOne": true, "includeParams": true },
    "http":       { "enabled": true, "max": 100 },
    "exceptions": { "enabled": true, "max": 50 },
    "messages":   { "enabled": true, "max": 200 },
    "timers":     { "enabled": true, "max": 200 },
    "scopes":     { "enabled": true, "form": true, "url": true, "cookie": false, "session": false, "request": false, "application": false, "maxDepth": 4, "maxBytes": 65536 },
    "jvm":        { "enabled": true, "threadDump": false },
    "cache":      { "enabled": false },
    "logs":       { "enabled": false, "minLevel": "INFO" },
    "session":    { "enabled": false },
    "modules":    { "enabled": true }
  }
}
```

Config is parsed once at activation into an immutable `LensConfig`. Unknown keys are logged as warnings.

Per request overrides: `lensEnable()` / `lensDisable()`, and a cookie/header `X-BxLens: on|off` honored only when `access` allows the caller.

## 5. BIF / component API

| Name | Signature | Notes |
|---|---|---|
| `lensMessage` | `( message, label="info" )` | `label`: info, warn, error, debug or any string. Structs/arrays are serialized. |
| `lensStart` | `( label ) : id` | Starts a timer. |
| `lensStop` | `( labelOrId )` | Ends a timer. Unmatched labels are ignored. |
| `lensMeasure` | `( label, callable ) : any` | Times a closure and returns its result. |
| `lensAddMeasure` | `( label, startMs, endMs )` | Manual span. |
| `lensException` | `( exception )` | Records a caught exception. |
| `lensEnable` / `lensDisable` | `()` | Per request switch (still subject to `access`). |
| `lensRender` | `() : string` | Manual render when `inject=false`. |
| `lensIsEnabled` | `() : boolean` | New. |
| `lensDump` | `( value, label="" )` | New. Pushes a value to the Messages panel with a collapsible tree. |
| `bx:lens` | `action="message|start|stop|dump"` | New. Component form for templates. |

Removed from the default surface: `lensDumpHeap`, `lensThreadDump`. They stay behind `collectors.jvm.threadDump` and a separate opt-in `allowHeapDump` setting, and never write outside the temp dir.

## 6. Injection

- Hook: interceptor on `onRequestEnd` with fallbacks on `onError` and `onAbort`. Pattern follows web-support `HtmlBody`.
- Inject only when: lens enabled, caller allowed, response `Content-Type` starts with an entry in `contentTypes`, status is not a redirect, response not already committed, request not matched by `excludePaths`.
- Place before the last `</body>` (case-insensitive search from the end, no full DOM parse). If absent, append.
- Non-HTML (JSON, SSE, files, redirects): no bar. When `history.trackNonHtml` is on (default), the request is still collected and stored, and the id is returned in the `X-BxLens-Id` header so it appears under History. History is a ring buffer: when full, the oldest request is recycled.
- The bar markup is a small container plus a JSON payload (`<script type="application/json">`, `</` escaped). In this draft, behavior and styles load from `/~bxlens/`. v1 inlines them instead.

## 7. Security

- Default `enabled=false`. When on, default access is loopback and private networks only.
- Redaction is server side, before serialization, on scopes, headers, params and messages.
- Payload size caps per collector (`maxBytes`, `max`).
- No eval in the UI: use the Alpine CSP build (`@alpinejs/csp`), assets served from the module, no inline handlers.
- Heap dump and thread dump are opt-in.
- JSON is embedded with `<`, `>`, `&`, U+2028, U+2029 escaped as unicode escapes, not HTML entities.

## 8. UI

Decisions (from design review):
- Collapsed health strip by default: status, time, memory, query and template counts. Strip border and an issues chip turn amber or red on slow request, N+1, caught exception, or 4xx/5xx.
- Auto-opens to the relevant tab when an exception is caught or thrown (`ui.autoOpenOnException`).
- Hero view is a unified waterfall: templates, functions, queries and HTTP calls on one time axis with nesting. Must-haves: hover details and click-to-expand drawer, zoom and pan the time axis, filter by type plus text search, open in editor from any row.
- Issues tab lists everything suspicious (exceptions, N+1, slow queries, slow templates) ranked, each linking to its row.
- Docked at the bottom, resizable by dragging the top edge, and detachable into a floating window inside the page. Open tab, height and collapsed state persist per browser.
- Hotkeys: Ctrl+` toggles, 1-9 switch tabs, / focuses search.
- Copy actions: SQL with params, file:line, request JSON, cURL.
- History shows HTML, JSON, SSE and other requests, with type, status, time and issue count. Compare-two-requests is post-v1.
- Compact devtools density. Brand gradient used sparingly (active tab, healthy status), not as a wash.

Alpine.js (kept per decision). Mockup: https://claude.ai/artifact/AQsvxZRZrKqWraet41gKno

Palette from `ortus-artwork/boxlang`: gradient `#00DBFF` to `#00FF75`, dark ground derived from `#303446`. Light and dark themes. Panels: Timeline (waterfall), Queries, Templates (include/call tree), HTTP, Exceptions, Messages, Timers, Request, Scopes, JVM, History.

## 8b. Module extension API (modules contribute collectors and panels)

Problem: every BoxLang module has its own class loader, so another module cannot implement Java interfaces that live inside bx-lens. The contract therefore has to be data-first, with Java as an optional second tier.

**Tier 1: data-only contributions (v1).** No shared classes. Works from BoxLang or Java modules.

1. bx-lens registers its own interception points in `configure()` of `ModuleConfig.bx`:

| Point | When | Payload |
|---|---|---|
| `onLensRegister` | at bx-lens activation and again on `postModuleLoad` | `registry` with `registry.panel( id, label, icon, order, renderer )` |
| `onLensRequestStart` | per request, after `LensRequest` exists | `context, requestId` |
| `onLensCollect` | per request, before render | `context, requestId, panel( id )` handle to add data |
| `onLensRequestFinish` | per request, after store | `requestId, summary` |

2. A module declares panels once and fills them per request:

```javascript
// in another module's ModuleConfig.bx
function configure() {
  interceptors = [ { class: "#moduleMapping#.interceptors.MyLens", name: "MyLens@mymodule" } ];
}
// MyLens.bx
@InterceptionPoint
function onLensRegister( event ) {
  event.registry.panel( id: "orm", label: "ORM", icon: "database", order: 40, renderer: "table" );
}
@InterceptionPoint
function onLensCollect( event ) {
  event.panel( "orm" )
    .columns( [ "Entity", "Action", "ms" ] )
    .rows( ormStats.rows )
    .badge( ormStats.count, ormStats.slow ? "warn" : "none" );
}
```

3. App code can use the same thing without a module through BIFs: `lensPanel( id, label ).table( ... )`, `.kv( ... )`, `.tree( ... )`, `.spans( ... )`, `.messages( ... )`, `.json( ... )`.

4. Renderers are built in, so module panels need no JavaScript: `table`, `kv`, `tree`, `spans` (feeds the waterfall with a type, label, start, duration), `messages`, `json`, `text`. Spans from any panel can appear in the Timeline with their own color and filter chip.

5. Contributions can add Issues (`panel.issue( severity, title, detail, file, line )`) which show on the Issues tab and drive the strip color.

6. Safety: payloads go through the same redaction, size caps and JSON escaping as built-in collectors. Text is always escaped by the UI. No module-supplied HTML or script in Tier 1.

**Tier 2: Java collectors (v1.x).** For hot paths where a module wants typed, fast collection. Options, to decide: publish a tiny `bx-lens-api` jar (interfaces only) that modules include in their own `libs/`, and bx-lens talks to it reflectively or via a `ServiceLoader` keyed on interface name; or keep Java modules on Tier 1 and let them call a static facade. This needs a spike on module class loader parents before we commit.

**Tier 3: custom UI (later).** A module may ship a small ES module served from `/~bxlens/ext/{module}/` and declared with `renderer: "custom"`. Disabled by default behind `ui.allowCustomPanels`, since it runs script in the page.

Candidate first-party contributors: bx-orm (entity loads and flushes), bx-redis (commands), bx-mail (sent mail), bx-ai (calls and tokens), bx-jdbc drivers (pool stats), Quick/qb, ColdBox/cbwire (event, handler, layout, view).

## 9. Delivery plan

| # | Task | Output |
|---|---|---|
| 1 | Approve mockup and this spec | decisions below |
| 2 | Repair build: `LensService` API, registration, delete `LensCollector`, fix tests/package | green `./gradlew test` |
| 3 | Dependency update: BoxLang 1.13.0 to current, Gradle plugins, test libs | `dependencyUpdates` clean |
| 4 | Core: `LensConfig`, `LensRequest`, `RequestStore`, access guard, redaction | unit tests |
| 5 | Injector + asset endpoint | bar renders in MiniServer |
| 6 | Collectors wave 1: request, timeline, templates, queries, exceptions, messages, timers | per-collector tests |
| 7 | Collectors wave 2: http, scopes, jvm, functions, logs, cache, session, modules | per-collector tests |
| 8 | UI build-out from mockup, History, ajax tracking | manual pass in MiniServer |
| 9 | Docs, readme, changelog, CI | release candidate |

## 10. Open items

1. Cache hit/miss, SOAP, `onComponentInvocation` and `postBIFInvocation` need core changes (section 3.7). Decide: file Jira issues and skip in v1, or fix in core first.
2. Confirm servlet and CommandBox paths run the same `onRequestEnd` flow as MiniServer.
3. Should `History` survive an app reinit (global store) or reset with it?
4. Editor link path mapping for Docker/remote (`remoteBase` to `localBase`) is included; confirm it is wanted in v1.
