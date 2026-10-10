---
title: Configuration
order: 3
description: Every BX Lens setting with its default and meaning.
icon: lucide:sliders-horizontal
---

# Configuration

Lens reads its settings from `boxlang.json` under `modules.bxLens.settings`. You only set what you want to change. The schema and defaults come from the module's `ModuleConfig.bx`. Restart the runtime (or reload the module) after a change to `boxlang.json`. A group of settings can also be changed live from the console, see [Live settings](#live-settings).

Lens is off by default. There are two surfaces and you switch them on independently:

- the **bar**, a strip injected into HTML pages for developers (`bar.enabled`)
- the **console**, a standalone page at `/~bxlens/index.bxm` for one server (`console.enabled`)

```json title="boxlang.json"
{
	"modules": {
		"bxLens": {
			"settings": {
				"bar": { "enabled": true },
				"thresholds": { "slowQueryMs": 50 }
			}
		}
	}
}
```

## Defaults

```json title="Full default settings"
{
	"bar": { "enabled": false, "access": "local", "allowAllIPs": false },
	"console": {
		"enabled": false,
		"password": "",
		"viewerPassword": "",
		"requireHttps": false,
		"access": "local",
		"sessionMinutes": 30,
		"maxLoginAttempts": 5,
		"lockoutMinutes": 5,
		"actions": true,
		"readOnly": false,
		"overridesFile": "",
		"runTimeoutSeconds": 60,
		"maxStreams": 10
	},
	"collect": { "level": "light" },
	"tabs": { "hide": [] },
	"store": { "enabled": true, "dir": "", "retentionHours": 72, "maxMB": 50, "flushSeconds": 30 },
	"ai": {
		"enabled": false,
		"provider": "ollama",
		"model": "llama3.2",
		"baseUrl": "http://localhost:11434",
		"embeddingModel": "nomic-embed-text",
		"temperature": "0.2",
		"timeoutSeconds": 120,
		"maxToolCalls": 8,
		"memoryMessages": 20,
		"maxConcurrentChats": 3,
		"actions": true,
		"rag": true,
		"apiKey": "",
		"apiKeyEnv": "",
		"links": true
	},
	"server": { "name": "", "address": "", "id": "" },
	"checks": { "securityHeaders": true, "slowSample": true },
	"dev": { "reloadAssets": false, "license": "" },
	"access": {
		"allowedHosts": [],
		"requireHeader": "",
		"trustProxyHeader": true,
		"proxyHeader": "X-Forwarded-For",
		"proxyPeers": "private"
	},
	"async": { "enabled": true, "queueSize": 2000 },
	"request": { "maxMinutes": 10 },
	"inject": true,
	"contentTypes": [ "text/html" ],
	"excludePaths": [ "/~bxlens/*", "/favicon.ico" ],
	"history": { "trackNonHtml": "", "header": "X-BxLens-Id", "headerAlways": true, "serverHeader": false, "maxRequests": 50 },
	"ui": {
		"theme": "auto",
		"startOpen": false,
		"autoOpenOnException": true,
		"defaultTab": "timeline",
		"height": 360,
		"allowDetach": true,
		"hotkey": "Ctrl+`"
	},
	"thresholds": { "slowRequestMs": 500, "slowQueryMs": 25, "slowTemplateMs": 100, "nPlusOneMin": 3 },
	"editor": { "linkPattern": "vscode://file/{path}:{line}", "remoteBase": "", "localBase": "" },
	"redact": {
		"keys": [ "password", "pwd", "passwd", "token", "secret", "apikey", "api_key", "authorization", "cookie", "credential" ],
		"mask": "[redacted]"
	},
	"limits": { "maxString": 2000, "maxDepth": 4, "maxItems": 100 },
	"collectors": {
		"templates": { "enabled": true, "max": 300 },
		"functions": { "enabled": false, "max": 1000, "minMs": 0 },
		"queries": { "enabled": true, "max": 200, "includeParams": false, "captureCaller": true },
		"http": { "enabled": true, "max": 100 },
		"exceptions": { "enabled": true, "max": 50 },
		"messages": { "enabled": true, "max": 200 },
		"timers": { "enabled": true, "max": 200 },
		"transactions": { "enabled": true, "max": 50 },
		"logs": { "enabled": false, "max": 200 },
		"scopes": {
			"enabled": true, "url": true, "form": true, "cookie": false,
			"session": false, "request": false, "application": false, "variables": false
		},
		"jvm": { "enabled": true },
		"cache": { "enabled": true },
		"modules": { "enabled": true },
		"executors": { "enabled": true },
		"tasks": { "enabled": true },
		"datasources": { "enabled": true },
		"caches": { "enabled": true },
		"logfiles": { "enabled": true },
		"environment": { "enabled": true },
		"inflight": { "enabled": true },
		"errors": { "enabled": true },
		"reports": { "enabled": true },
		"ask": { "enabled": true },
		"system": { "enabled": true },
		"threads": { "enabled": true }
	}
}
```

`console.allowHeapDump` (default `false`) is read by the code but is not in the declared schema, so it is not in the block above. It is described under [`console`](#console).

## `bar`

The bar shows only to callers the rule allows. Everyone else is not tracked for the bar. See [Security](security.md).

| Key | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `false` | Show the bar to allowed callers. |
| `access` | string or list | `"local"` | Who may see the bar. `"local"` (loopback only), `"all"`, or a list of exact IPs, CIDR ranges and the words `local` and `private`. `private` means loopback plus 10/8, 172.16/12, 192.168/16, fc00::/7 and link-local. |
| `allowAllIPs` | boolean | `false` | A second key for `"all"`. Without it, `"all"` falls back to loopback and Lens logs an error. |

## `console`

The console needs `enabled: true` and a password. See [Console](console/index.md).

| Key | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `false` | Turn the console on. It also starts collecting every request. |
| `password` | string | `""` | The admin password. Use a `bxsecret:` value. An empty or undecryptable value keeps the console unavailable (the page answers 503 with setup help). |
| `viewerPassword` | string | `""` | An optional second password for the viewer role. A viewer can look at every page but cannot change anything, and cannot download thread dumps, heap dumps, log files or the diagnostic bundle, or read cache values. Use a `bxsecret:` value. It is ignored when `password` is empty. See [Roles](security.md#roles). |
| `requireHttps` | boolean | `false` | Refuse the console over plain HTTP with a 403. Loopback callers are exempt. Turn it on in production. When it is off and a non-loopback caller uses HTTP, the console shows a warning banner. |
| `access` | string or list | `"local"` | Same rule format as `bar.access`. `"all"` needs no second key but logs a warning. A caller that is not allowed gets a plain 404. |
| `sessionMinutes` | number | `30` | Idle timeout. A session also ends 12 hours after login. |
| `maxLoginAttempts` | number | `5` | Wrong passwords per address before a lockout. |
| `lockoutMinutes` | number | `5` | How long the address is locked out. |
| `actions` | boolean | `true` | Allow pause, resume, run and reload of scheduled tasks. These also need BoxLang+ or a trial. When false the buttons are disabled and the server refuses the calls. |
| `readOnly` | boolean | `false` | View only. The console refuses every change: settings, task actions, the bar layout, cache clear, evict and reap, resets of the Queries, Errors and Reports counters, and Run GC. It does not block heap dumps, which have their own switch, and it does not block the connection test or the AI calls. It can only be set in `boxlang.json`. |
| `overridesFile` | string | `""` | Where changes made on the Settings page are saved. Empty means `config/bxlens-settings.json` in the BoxLang home. See [Live settings](#live-settings). |
| `allowHeapDump` | boolean | `false` | Offer a heap dump on the System page, to admins only. Heap dumps also need BoxLang+ or a trial. A heap dump holds everything in memory, including secrets, so read [Security](security.md#heap-dumps) first. Not in the declared schema. |
| `runTimeoutSeconds` | number | `60` | How long Run now waits for a task before it reports that the task is still running. |
| `maxStreams` | number | `10` | Most live (Server-Sent Events) streams at once. More get a 429 and the browser polls. |

## `collect`

| Key | Type | Default | Description |
|---|---|---|---|
| `level` | string | `"light"` | `off`, `light` or `full`. `off` collects nothing. `light` is the default and is for production: it skips request headers, bound query parameters, the caller (template and line) of queries, outgoing HTTP calls and transactions, BoxLang frames of exceptions, scope contents, Java stack traces, and the `functions`, `logs`, `scopes` and `bifs` collectors. SQL text, timings and counts are kept. Use `full` while you develop. |

## `tabs`

| Key | Type | Default | Description |
|---|---|---|---|
| `hide` | list | `[]` | Ids of bar tabs and console pages to hide. Bar ids: `timeline`, `queries`, `exceptions`, `http`, `messages`, `timers`, `request`, `scopes`, `bifs`, `runtime`, plus ids of custom panels. Console ids: `overview`, `requests`, `inflight`, `errors`, `reports`, `ask`, `queries`, `executors`, `tasks`, `datasources`, `caches`, `logfiles`, `environment`, `configuration`, `system`, `threads`, `designer`. The id `queries` hides both the bar tab and the console page. Settings is always shown. A disabled collector hides its tab too. |

## `access`

These extra checks apply to the bar and the console.

| Key | Type | Default | Description |
|---|---|---|---|
| `allowedHosts` | list | `[]` | Restrict to these `Host` header names. Empty means: any host, except that a guard whose rule is exactly `"local"` accepts only the names `localhost`, `127.0.0.1` and `[::1]` (this stops DNS rebinding, where a web page points its own name at 127.0.0.1). To reach a local-only bar or console under another name, such as `myapp.test` or a container name, list that name here. |
| `requireHeader` | string | `""` | Require a request header: `"Name"` or `"Name=value"`. Empty means none. |
| `trustProxyHeader` | boolean | `true` | Behind a proxy or load balancer, take the client address from `proxyHeader`. The header is only believed when the direct connection comes from one of `proxyPeers`. The same rule decides whether `X-Forwarded-Proto` is believed for the HTTPS check. |
| `proxyHeader` | string | `"X-Forwarded-For"` | The header that carries the client address. With a list of addresses, Lens reads it from the right and takes the first address that is not a trusted proxy. |
| `proxyPeers` | string or list | `"private"` | Which direct connections may set the header: `private` (loopback and private networks), `local`, or a list of exact IPs and CIDR ranges. A header can never make a remote peer look like loopback. |

See [Security](security.md#behind-a-proxy).

## Core

| Key | Type | Default | Description |
|---|---|---|---|
| `inject` | boolean | `true` | Inject the bar before `</body>` on HTML responses. Set false and call [`lensRender()`](guides/bifs.md#lensrender) to place it yourself. |
| `contentTypes` | list | `["text/html"]` | Response content types that get the bar. |
| `excludePaths` | list | `["/~bxlens/*", "/favicon.ico"]` | Paths that are never tracked. A trailing `*` is a prefix match. Console paths are always excluded. |

## `async`

| Key | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Run the work a request does not need to wait for (statistics, the history, the audit log) on one background thread. `false` runs it in the request. `boxlang.json` only. |
| `queueSize` | number | `2000` | How many tasks may wait. When the queue is full new tasks are dropped and counted (never the request, which never waits). The console Overview and System pages and `lensDiagnostics()` show the depth and the dropped count. `boxlang.json` only. |

## `request`

| Key | Type | Default | Description |
|---|---|---|---|
| `maxMinutes` | number | `10` | A request still running after this many minutes is finished by the watchdog as **unfinished**: its open spans are closed with an estimate and marked interrupted, it is kept in the console history with the issue "Request never finished", and it leaves the In flight list. Nothing is written to its response. The check runs every five seconds. |

## `history`

| Key | Type | Default | Description |
|---|---|---|---|
| `trackNonHtml` | boolean | empty | Record JSON, SSE, file and redirect requests too. They show no bar. Empty means off at `collect.level` `light` and on at `full`. A request that is not kept costs no snapshot, no issue analysis and no history entry; the console totals still count it. |
| `header` | string | `"X-BxLens-Id"` | Response header that carries the request id. |
| `headerAlways` | boolean | `true` | Send the id header on every tracked request, not only when the bar is shown. The id is short and unique, not a secret. It is also `request.bxlens.id` in the request scope, the result of `lensRequestId()`, and the logging context key `requestId` (SLF4J MDC) for the request thread, so a log pattern with `%X{requestId}` prints it. |
| `serverHeader` | boolean | `false` | Also send `X-BxLens-Server` with the id of this server (see [`server`](#server)), next to the request id header and only when that header is sent. Off by default because it names a machine to whoever sees the response. |
| `maxRequests` | number | `50` | Size of the in-memory ring buffer. The oldest request is recycled when full. On Free the buffer is capped at 25. See [Licensing](licensing.md#free-and-boxlang). |

## `server`

Which machine produced a record. Every tracked request, error group and sample, report, query statistic, in-flight entry, history entry, export, audit line and file of the disk store carries this identity, so the data of many servers can be sent to one place and told apart. See [Server identity](features.md#server-identity).

Lens detects the identity once at start and looks again at most every five minutes. It never does a lookup while a request runs. Leave the keys empty to detect. Set them when the machine does not report what you want, for example in a container or behind NAT.

| Key | Type | Default | Description |
|---|---|---|---|
| `name` | string | `""` | The host name. Empty reads the environment variable `LENS_SERVER_NAME`, then asks the operating system, then reads `HOSTNAME` or `COMPUTERNAME`. |
| `address` | string | `""` | The primary address. Empty reads `LENS_SERVER_ADDRESS`, then takes the first IPv4 address of an interface that is up, not loopback and not virtual, then IPv6, else `127.0.0.1`. The other addresses are listed too. |
| `id` | string | `""` | The id of this instance. Empty reads `LENS_SERVER_ID`, then makes a short id from the host name and the start time of the JVM, so it is stable for one start and differs after a restart. Set it when a fixed id per server is wanted. |

All three are `boxlang.json` only. The BoxLang runtime instance name is read from the environment variable `BOXLANG_INSTANCE_NAME` or the system property `boxlang.instanceName` when one is set.

## `ui`

| Key | Type | Default | Description |
|---|---|---|---|
| `theme` | string | `"auto"` | `auto` follows the OS. Or `dark`, `light`. |
| `startOpen` | boolean | `false` | Start with the panel open instead of the collapsed strip. |
| `autoOpenOnException` | boolean | `true` | Open the panel on Issues when an exception was caught. |
| `defaultTab` | string | `"timeline"` | Tab shown first. |
| `height` | number | `360` | Initial panel height in pixels. |
| `allowDetach` | boolean | `true` | Allow the floating detached window. |
| `hotkey` | string | ``"Ctrl+`"`` | Toggle shortcut. Modifiers: Ctrl, Alt, Shift, Meta. |

## `thresholds`

| Key | Type | Default | Description |
|---|---|---|---|
| `slowRequestMs` | number | `500` | Requests slower than this raise an issue. Also starts the [slow request sample](console/issues.md#slow-request-sample). |
| `slowQueryMs` | number | `25` | Queries slower than this warn. At 4 times this value they are critical. |
| `slowTemplateMs` | number | `100` | Templates and functions with a self time above this raise an issue. |
| `nPlusOneMin` | number | `3` | Runs of the same SQL needed to flag an N+1. |

## `checks`

| Key | Type | Default | Description |
|---|---|---|---|
| `securityHeaders` | boolean | `true` | Add Notes for missing security headers and cookie flags on HTML responses. See [Issues](console/issues.md#security-notes). |
| `slowSample` | boolean | `true` | Sample the request thread once when it passes `thresholds.slowRequestMs`. |

## `store`

The disk store keeps errors and reports across restarts. It needs BoxLang+ or a trial. Without one, Errors and Reports stay in memory and `store` has no effect. See [Licensing](licensing.md#free-and-boxlang).

| Key | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Use the disk store when the license allows it. |
| `dir` | string | `""` | Folder for `errors.json` and `reports.json`. Empty means `lens-data` in the BoxLang home. In a container, point it at a mounted volume. |
| `retentionHours` | number | `72` | How long saved errors and the minute series are kept. |
| `maxMB` | number | `50` | Size limit for the saved errors. When the file would be larger, the oldest half of the groups is dropped. |
| `flushSeconds` | number | `30` | How often changes are written (5 seconds at the least). Files are written to a temporary file and renamed. |

## `ai`

Optional help from a language model: the [Lensy](console/ai.md), Explain with AI and Ask Lens. Off by default. See [Ask Lens and AI help](console/ask-lens-and-ai.md), [Set up Ollama](guides/ollama.md) and the [data flow](security.md#ai-data-flow). Everything but `apiKey` and `links` can be changed live from the AI page.

| Key | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `false` | Let the server send prompts and tool results to a model through the `bx-ai` module. The module ships inside Lens, and the server calls also need BoxLang+ or a trial. See [Licensing](licensing.md#free-and-boxlang). |
| `provider` | string | `"ollama"` | `ollama`, `openai`, `claude`, `gemini`, `mistral`, `groq`, `grok`, `deepseek`, `openrouter`, `openai-compatible`, `cohere` or `docker`. |
| `model` | string | `"llama3.2"` | The model name. Empty uses `llama3.2` for Ollama and the provider default otherwise. |
| `baseUrl` | string | `"http://localhost:11434"` | The address of the model server. `http` or `https`, no user name or password in it. Empty uses the local Ollama address for Ollama and the provider default otherwise. |
| `embeddingModel` | string | `"nomic-embed-text"` | The model that turns documentation sections into vectors for `searchDocs`. |
| `temperature` | string | `"0.2"` | 0 to 2. |
| `timeoutSeconds` | number | `120` | Seconds an answer may take (10 to 600). The time a person takes to approve an action is added. |
| `maxToolCalls` | number | `8` | Tool calls allowed in one answer (1 to 20). |
| `memoryMessages` | number | `20` | Messages one console session remembers (2 to 100). In memory only, gone at logout, timeout or reset. |
| `maxConcurrentChats` | number | `3` | Chats that may run at once on this server (1 to 20). The next one gets 429. |
| `actions` | boolean | `true` | Let the assistant propose actions. Each one still needs an Approve click. |
| `rag` | boolean | `true` | Let the assistant search the bundled documentation. |
| `apiKey` | string | `""` | The API key as a `bxsecret:` value. This is the one setting the console can never change. Leave it empty for a local model. |
| `apiKeyEnv` | string | `""` | The **name** of an environment variable that holds the key. It wins over `apiKey`. The console refuses anything that is not a variable name. |
| `links` | boolean | `true` | Show Copy prompt and the Ask ChatGPT and Ask Claude buttons. They send nothing from the server. |

### MCP servers

The MCP servers Lensy may ask are not in `boxlang.json`. The admin keeps them on the AI page, and they are saved with the other console settings in `config/bxlens-settings.json` (or `console.overridesFile`) under `mcp.servers`:

```json
{
  "overrides": { },
  "mcp": {
    "servers": [
      { "id": "boxlang", "builtin": true, "enabled": true, "allowedTools": ["*"] },
      { "id": "acme", "name": "Acme", "url": "https://mcp.example.com/mcp", "builtin": false, "enabled": true, "trusted": false, "allowedTools": ["lookup"] }
    ]
  }
}
```

The file is checked when it is read, like the console settings: an entry with a bad address, name or id is skipped, listed on the AI page and in the log, and the rest is used. A built-in entry can only change `enabled` and `allowedTools`; its address always comes from Lens. See [Servers Lensy can ask](console/ai.md#servers-lensy-can-ask-mcp) for the rules.

## `editor`

| Key | Type | Default | Description |
|---|---|---|---|
| `linkPattern` | string | `"vscode://file/{path}:{line}"` | Click-to-editor link. `{path}` and `{line}` are replaced. The scheme must be one of `vscode:`, `vscode-insiders:`, `idea:`, `phpstorm:`, `subl:`, `file:`, `http:`, `https:`, `cursor:` or `zed:`. Any other scheme (such as `javascript:`) is replaced by the default on the server, and the Settings page refuses it. |
| `remoteBase` | string | `""` | Path prefix on the server, for example inside a container. |
| `localBase` | string | `""` | Path prefix on your machine that replaces `remoteBase`. |

```json title="Docker path mapping"
{
	"editor": { "remoteBase": "/app", "localBase": "/Users/me/projects/shop" }
}
```

## `redact`

| Key | Type | Default | Description |
|---|---|---|---|
| `keys` | list | `["password", "pwd", "passwd", "token", "secret", "apikey", "api_key", "authorization", "cookie", "credential"]` | A key is masked when its name contains any of these, ignoring case. |
| `mask` | string | `"[redacted]"` | Replacement text. |

## `limits`

Caps for any value sent to the page.

| Key | Type | Default | Description |
|---|---|---|---|
| `maxString` | number | `2000` | Longest string. |
| `maxDepth` | number | `4` | Deepest nesting. |
| `maxItems` | number | `100` | Most items in a collection. |

## `collectors`

Each collector powers one or more panels and has an `enabled` flag. Most also have a `max` that caps stored items per request.

| Collector | Key | Default | Description |
|---|---|---|---|
| `templates` | `enabled` | `true` | Template and include tree. |
| | `max` | `300` | Max template entries. |
| `functions` | `enabled` | `false` | Function calls. Off by default because it is a hot path. Skipped at `collect.level` `light`. |
| | `max` | `1000` | Max function entries. |
| | `minMs` | `0` | Skip calls faster than this. |
| `queries` | `enabled` | `true` | SQL queries. Also switches the console [Queries](console/in-flight-and-queries.md#queries) page, which uses the same id. |
| | `max` | `200` | Max query entries. |
| | `includeParams` | `false` | Show bound parameters. Off by default because bound values can be personal data. Off at `light` whatever this says. |
| | `captureCaller` | `true` | Record the template and line that ran each query. Off at `light`. |
| `http` | `enabled`, `max` | `true`, `100` | Outgoing HTTP calls. URLs are passed through the secret masker (user info and secret parameters are hidden), so Copy as cURL cannot expose a credential. |
| | `propagateId` | `false` | Add the request id as `X-Request-Id` to outgoing calls. Off by default. It only works when core hands Lens a request builder with the event; today it hands an immutable request, so this has no effect yet. `boxlang.json` only. |
| `exceptions` | `enabled`, `max` | `true`, `50` | Thrown and caught exceptions. |
| `messages` | `enabled`, `max` | `true`, `200` | Messages and dumps. |
| `timers` | `enabled`, `max` | `true`, `200` | Timers and measures. |
| `transactions` | `enabled`, `max` | `true`, `50` | Transactions, shown as spans on the Timeline. |
| `logs` | `enabled`, `max` | `false`, `200` | Log messages of one request, shown in Messages. Skipped at `light`. Not the console Logs page, which is `logfiles`. |
| `scopes` | `enabled` | `false` | Scope viewer. Opt in: the tab exists only when this is on. Skipped at `light`. |
| | `url`, `form` | `true` | Include these scopes. |
| | `cookie`, `session`, `request`, `application`, `variables` | `false` | Include these scopes. Opt in. |
| `jvm` | `enabled` | `true` | Runtime tab of the bar: a snapshot of the runtime, cached for a few seconds. |
| `modules` | `enabled` | `true` | Console [Modules](console/modules.md) page. |
| `configuration` | `enabled` | `true` | Console [Configuration](console/configuration.md) page. |
| `orm` | `enabled` | `false` | The bx-orm [integration](reference/integrations.md): listens to `onORMQuery`, `onORMFlush` and `onORMException` and shows the SQL like a normal query. Opt in and needs the bx-orm module (1.7.2 or later) installed: until both are true Lens registers no listener. Live: an admin can switch it from the console Modules or ORM page. The console [ORM](panels/orm.md) page is always listed and shows the state. Parameter values need `announceQueryParams` in the ORM application's `ormSettings` and `queries.includeParams` here. |
| `bifs` | `enabled` | `false` | Time per built-in function, from `postBIFInvocation`. Off by default and skipped at `light`, because every BIF call allocates an event while it is on. Needs a BoxLang build with core pull request 657. Lens's own `lens*` functions are left out, a request keeps up to 300 names, and the [panel](panels/bifs.md) shows the top 60 by total time. |
| `executors` | `enabled` | `true` | Console [Executors](console/executors.md) page. |
| `tasks` | `enabled` | `true` | Console [Tasks](console/tasks.md) page. |
| `datasources` | `enabled` | `true` | Console [Datasources](console/datasources.md) page. |
| `caches` | `enabled` | `true` | Console [Caches](console/caches.md) page. |
| `logfiles` | `enabled` | `true` | Console [Logs](console/logs.md) page. |
| `environment` | `enabled` | `true` | Console [Environment](console/environment.md) page and the diagnostic bundle. |
| `inflight` | `enabled` | `true` | Console [In flight](console/in-flight-and-queries.md#in-flight) page. |
| `errors` | `enabled` | `true` | Console [Errors](console/errors-and-reports.md#errors) page. |
| `reports` | `enabled` | `true` | Console [Reports](console/errors-and-reports.md#reports) page. |
| `ask` | `enabled` | `true` | Console [Ask Lens](console/ask-lens-and-ai.md) page. |
| `system` | `enabled` | `true` | Console [System](console/system-and-threads.md) page. |
| `threads` | `enabled` | `true` | Console [Threads](console/system-and-threads.md#threads) page. |

The ids `logs` (the log messages of one request, shown in Messages) and `logfiles` (the console Logs page) are different collectors. The id `queries` is shared: `collectors.queries.enabled` switches the query collector and the console Queries page together.

## `dev`

| Key | Type | Default | Description |
|---|---|---|---|
| `reloadAssets` | boolean | `false` | Serve the UI files from disk on every request, so edits show on refresh. The harness turns it on. See [Development](project/contributing.md#the-harness). Leave it off otherwise. |
| `mcpBuiltinBase` | string | `""` | For tests only. When set, the built-in MCP documentation servers are reached at `base/id` (for example `http://127.0.0.1:11435/mcp.bxs/boxlang`) instead of their real addresses, so the end to end tests need no internet. Only `boxlang.json` can set it; the console cannot. |
| `license` | string | `""` | Force a license state for demos: `trial`, `plus`, `expired` or `none`. Empty detects the real state. See [Licensing](licensing.md). |

## Live settings

The Settings page of the console can change many settings without a restart. A change applies at once, is saved to `config/bxlens-settings.json` in the BoxLang home (or the file in `console.overridesFile`), and is loaded again at the next start. A saved change wins over `boxlang.json`. The page marks it `changed` and offers a reset, which removes the override and brings back the value from `boxlang.json`. See [Console settings](console/settings.md).

Which settings are live is fixed in the code (`SettingsRegistry`). Nothing else can be changed from the browser.

| Live (console and `boxlang.json`) | `boxlang.json` only |
|---|---|
| `bar.enabled`, `inject`, `collect.level`, `tabs.hide`, `history.trackNonHtml` | `console.*` (including both passwords, `access`, `readOnly`, `requireHttps`, `allowHeapDump`, `overridesFile`) |
| `collectors.<id>.enabled` for every collector, `collectors.queries.includeParams`, `collectors.queries.captureCaller` | `access.*` |
| `thresholds.*` | `bar.access`, `bar.allowAllIPs` |
| `checks.*` | `history.maxRequests`, `history.header` |
| `ui.*`, `limits.*`, `editor.*`, `ai.*` (except `ai.apiKey` and `ai.links`) | `store.*`, `ai.apiKey`, `ai.links` |
| | `redact.*`, `contentTypes`, `excludePaths`, `dev.*`, the `max` numbers of the collectors |

The locked settings that the page lists show their value and the label `boxlang.json only`. A password or API key shows only `set` or `not set`. A key that is not on the live list is refused by the server with an error. If `console.readOnly` is true, or the caller is a viewer, nothing can be changed. A value that is not valid, for example a number out of range, is refused and nothing is saved. An entry that is not valid in the saved file is ignored at the next start and a warning is logged.

## Moving from older settings

Earlier builds had one top-level switch and one access rule for the bar. They map like this.

| Old setting | New setting |
|---|---|
| `enabled` | `bar.enabled` |
| `access.allowedIPs` | `bar.access` (a list of IPs and CIDRs, or `"local"`) |
| `access.allowPrivateNetworks: true` | add the word `private` to `bar.access` |
| `access.allowedIPs: ["*"]` | `bar.access: "all"` plus `bar.allowAllIPs: true` |
| `access.allowedHosts`, `access.requireHeader` | unchanged, now also apply to the console |
| `ai.provider: ""` (use the bx-ai default) | the default is now `"ollama"` with `ai.model: "llama3.2"` at `ai.baseUrl: "http://localhost:11434"`. Set `provider` and `model` to keep another provider |
| `collectors.orm.statistics` | removed. Lens no longer forces Hibernate statistics on. Set `generateStatistics` in the ORM application's `ormSettings`, or press Turn statistics on on the console ORM page |

`checks.*` and `dev.*` were read with defaults but not declared in the schema. They are declared now, with the same names and defaults, so no change is needed. `console.allowHeapDump` is the one key that is still read without being declared.

The old default allowed private networks. The new default, `"local"`, is loopback only. To keep the old behavior set `"bar": { "enabled": true, "access": [ "local", "private" ] }`.

## Per request overrides

`lensEnable()` and `lensDisable()` switch Lens for one request. The access rules still apply.
