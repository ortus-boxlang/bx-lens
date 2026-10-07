# BX Lens: API and Shape Spec (DRAFT for approval)

Request-level debug bar for BoxLang web apps (MiniServer, servlet, CommandBox). In-memory only. HTML responses only. Open source. Observability across requests is out of scope (that is BX Insights).

## 1. Architecture

| Piece | Responsibility |
|---|---|
| `LensService` (global service) | Owns config, collector registry, `RequestStore`, `GlobalStats`. |
| `LensRequest` | Typed per-request data. Stored as a context attachment on the request context. |
| `ILensCollector` | SPI. One class per panel. Java only. Registered as interceptors by the service. |
| `RequestStore` | Bounded ring buffer of finished `LensRequest` snapshots (default 50). No disk, no DB. |
| `LensInjector` | Interceptor that renders the bar into HTML responses at end of request. |
| Asset endpoint | Serves `lens.js`, `lens.css`, `alpine.min.js` and JSON for stored requests from `/~bxlens/`. |

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

## 3. Events used (verified against BoxLang 1.19 `BoxEvent`)

| Collector | Events |
|---|---|
| request | `onRequestStart`, `onRequestEnd`, `onError`, `onAbort` |
| timeline | all collectors feed spans |
| templates | `preTemplateInvoke`, `postTemplateInvoke` |
| functions | `preFunctionInvoke`, `postFunctionInvoke`, `onFunctionException` (stack per thread, off by default) |
| queries | `preQueryExecute`, `postQueryExecute`, `onTransaction*` |
| http | `onHTTPRequest`, `onHTTPRawResponse`, `onHTTPResponse`, `onHTTPError` |
| exceptions | `onError`, `onFunctionException`, plus `lensException()` |
| cache | `afterCacheElementInsert`, `afterCacheElementUpdated`, `afterCacheElementRemoved` (hit/miss needs a core event; see open items) |
| logs | `logMessage` |
| session | `onSessionCreated`, `onSessionDestroyed` |
| app | `onApplicationStart`, `onApplicationEnd`, `onApplicationRestart` |

Not available in core, so removed from the current code: `onException`, `onSOAPRequest`, `onSOAPResponse`.

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
  "ajax": { "track": true, "header": "X-BxLens-Id" },
  "storage": { "maxRequests": 50 },
  "ui": { "theme": "auto", "position": "bottom", "startOpen": false, "defaultTab": "timeline", "height": 340 },
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
- Non-HTML (JSON, SSE, files, redirects): no bar. When `ajax.track` is on, the request is still stored and the id is returned in the `X-BxLens-Id` header so the bar can list it under History.
- The bar markup is a small container plus a JSON payload (`<script type="application/json">`, `</` escaped). Behavior and styles load from `/~bxlens/` so they are cached across pages.

## 7. Security

- Default `enabled=false`. When on, default access is loopback and private networks only.
- Redaction is server side, before serialization, on scopes, headers, params and messages.
- Payload size caps per collector (`maxBytes`, `max`).
- No eval in the UI: use the Alpine CSP build (`@alpinejs/csp`), assets served from the module, no inline handlers.
- Heap dump and thread dump are opt-in.
- JSON is embedded with `<`, `>`, `&`, U+2028, U+2029 escaped as unicode escapes, not HTML entities.

## 8. UI

Alpine.js (kept per decision). Mockup: https://claude.ai/artifact/AQsvxZRZrKqWraet41gKno

Palette from `ortus-artwork/boxlang`: gradient `#00DBFF` to `#00FF75`, dark ground derived from `#303446`. Light and dark themes. Panels: Timeline (waterfall), Queries, Templates (include/call tree), HTTP, Exceptions, Messages, Timers, Request, Scopes, JVM, History.

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

1. Cache hit/miss and SOAP need new core events, or we skip them. Needs a decision.
2. Confirm servlet and CommandBox paths run the same `onRequestEnd` flow as MiniServer.
3. Should `History` survive an app reinit (global store) or reset with it?
4. Editor link path mapping for Docker/remote (`remoteBase` to `localBase`) is included; confirm it is wanted in v1.
