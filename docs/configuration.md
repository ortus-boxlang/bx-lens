---
title: Configuration
order: 3
description: Every BX Lens setting with its default and meaning.
icon: lucide:sliders-horizontal
---

# Configuration

Lens reads its settings from `boxlang.json` under `modules.bxLens.settings`. You only set what you want to change. The schema and defaults come from the module's `ModuleConfig.bx`. Restart the runtime (or reload the module) after a change.

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
		"access": "local",
		"sessionMinutes": 30,
		"maxLoginAttempts": 5,
		"lockoutMinutes": 5,
		"actions": true,
		"runTimeoutSeconds": 60,
		"maxStreams": 10
	},
	"collect": { "level": "full" },
	"tabs": { "hide": [] },
	"access": { "allowedHosts": [], "requireHeader": "" },
	"inject": true,
	"contentTypes": [ "text/html" ],
	"excludePaths": [ "/~bxlens/*", "/favicon.ico" ],
	"history": { "trackNonHtml": true, "header": "X-BxLens-Id", "maxRequests": 50 },
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
		"queries": { "enabled": true, "max": 200, "includeParams": true, "captureCaller": true },
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
		"system": { "enabled": true },
		"threads": { "enabled": true }
	}
}
```

Three more keys are read with their own defaults and are not part of the declared schema: `checks.securityHeaders` (`true`), `checks.slowSample` (`true`) and the `dev` block. They are listed below.

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
| `password` | string | `""` | The one login password. Use a `bxsecret:` value. An empty or undecryptable value keeps the console unavailable (the page answers 503 with setup help). |
| `access` | string or list | `"local"` | Same rule format as `bar.access`. `"all"` needs no second key but logs a warning. A caller that is not allowed gets a plain 404. |
| `sessionMinutes` | number | `30` | Idle timeout. A session also ends 12 hours after login. |
| `maxLoginAttempts` | number | `5` | Wrong passwords per address before a lockout. |
| `lockoutMinutes` | number | `5` | How long the address is locked out. |
| `actions` | boolean | `true` | Allow pause, resume, run and reload of scheduled tasks. When false the buttons are disabled and the server refuses the calls. |
| `runTimeoutSeconds` | number | `60` | How long Run now waits for a task before it reports that the task is still running. |
| `maxStreams` | number | `10` | Most live (Server-Sent Events) streams at once. More get a 429 and the browser polls. |

## `collect`

| Key | Type | Default | Description |
|---|---|---|---|
| `level` | string | `"full"` | `off`, `light` or `full`. `off` collects nothing. `light` is for production: it skips request headers, bound query parameters, the caller of each query, scope contents, Java stack traces, and the `functions`, `logs` and `scopes` collectors. SQL text, timings and counts are kept. |

## `tabs`

| Key | Type | Default | Description |
|---|---|---|---|
| `hide` | list | `[]` | Ids of bar tabs and console pages to hide. Bar ids: `issues`, `timeline`, `queries`, `templates`, `http`, `exceptions`, `messages`, `timers`, `cache`, `modules`, `request`, `scopes`, `jvm`, `history`, plus ids of custom panels. Console ids: `overview`, `requests`, `executors`, `tasks`, `system`, `threads`, `designer`. Settings is always shown. A disabled collector hides its tab too. |

## `access`

These extra checks apply to the bar and the console.

| Key | Type | Default | Description |
|---|---|---|---|
| `allowedHosts` | list | `[]` | Restrict to these `Host` header names. Empty means any host. |
| `requireHeader` | string | `""` | Require a request header: `"Name"` or `"Name=value"`. Empty means none. |

## Core

| Key | Type | Default | Description |
|---|---|---|---|
| `inject` | boolean | `true` | Inject the bar before `</body>` on HTML responses. Set false and call [`lensRender()`](guides/bifs.md#lensrender) to place it yourself. |
| `contentTypes` | list | `["text/html"]` | Response content types that get the bar. |
| `excludePaths` | list | `["/~bxlens/*", "/favicon.ico"]` | Paths that are never tracked. A trailing `*` is a prefix match. Console paths are always excluded. |

## `history`

| Key | Type | Default | Description |
|---|---|---|---|
| `trackNonHtml` | boolean | `true` | Record JSON, SSE, file and redirect requests too. They show no bar. |
| `header` | string | `"X-BxLens-Id"` | Response header that carries the stored request id. |
| `maxRequests` | number | `50` | Size of the in-memory ring buffer. The oldest request is recycled when full. |

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
| `slowRequestMs` | number | `500` | Requests slower than this raise an issue. Also starts the [slow request sample](panels/issues.md#slow-request-sample). |
| `slowQueryMs` | number | `25` | Queries slower than this warn. At 4 times this value they are critical. |
| `slowTemplateMs` | number | `100` | Templates and functions with a self time above this raise an issue. |
| `nPlusOneMin` | number | `3` | Runs of the same SQL needed to flag an N+1. |

## `checks`

Not in the declared schema. Both default to `true`.

| Key | Type | Default | Description |
|---|---|---|---|
| `securityHeaders` | boolean | `true` | Add Notes for missing security headers and cookie flags on HTML responses. See [Issues](panels/issues.md#security-notes). |
| `slowSample` | boolean | `true` | Sample the request thread once when it passes `thresholds.slowRequestMs`. |

## `editor`

| Key | Type | Default | Description |
|---|---|---|---|
| `linkPattern` | string | `"vscode://file/{path}:{line}"` | Click-to-editor link. `{path}` and `{line}` are replaced. |
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
| `queries` | `enabled` | `true` | SQL queries. |
| | `max` | `200` | Max query entries. |
| | `includeParams` | `true` | Show bound parameters. Off at `light`. |
| | `captureCaller` | `true` | Record the template and line that ran each query. Off at `light`. |
| `http` | `enabled`, `max` | `true`, `100` | Outgoing HTTP calls. |
| `exceptions` | `enabled`, `max` | `true`, `50` | Thrown and caught exceptions. |
| `messages` | `enabled`, `max` | `true`, `200` | Messages and dumps. |
| `timers` | `enabled`, `max` | `true`, `200` | Timers and measures. |
| `transactions` | `enabled`, `max` | `true`, `50` | Transactions, shown as spans on the Timeline. |
| `logs` | `enabled`, `max` | `false`, `200` | Log messages, shown in Messages. Skipped at `light`. |
| `scopes` | `enabled` | `true` | Scope viewer. Skipped at `light`. |
| | `url`, `form` | `true` | Include these scopes. |
| | `cookie`, `session`, `request`, `application`, `variables` | `false` | Include these scopes. Opt in. |
| `jvm` | `enabled` | `true` | Runtime panel. |
| `cache` | `enabled` | `true` | Cache panel. |
| `modules` | `enabled` | `true` | Modules panel. |
| `executors` | `enabled` | `true` | Console [Executors](console/executors.md) page. |
| `tasks` | `enabled` | `true` | Console [Tasks](console/tasks.md) page. |
| `system` | `enabled` | `true` | Console [System](console/system-and-threads.md) page. |
| `threads` | `enabled` | `true` | Console [Threads](console/system-and-threads.md#threads) page. |

## `dev`

Not in the declared schema.

| Key | Type | Default | Description |
|---|---|---|---|
| `reloadAssets` | boolean | `false` | Serve the UI files from disk on every request, so edits show on refresh. The harness turns it on. See [Development](project/contributing.md#the-harness). Leave it off otherwise. |
| `license` | string | `""` | Force a license state for demos: `trial`, `plus`, `expired` or `none`. Empty detects the real state. See [Licensing](licensing.md). |

## Moving from older settings

Earlier builds had one top-level switch and one access rule for the bar. They map like this.

| Old setting | New setting |
|---|---|
| `enabled` | `bar.enabled` |
| `access.allowedIPs` | `bar.access` (a list of IPs and CIDRs, or `"local"`) |
| `access.allowPrivateNetworks: true` | add the word `private` to `bar.access` |
| `access.allowedIPs: ["*"]` | `bar.access: "all"` plus `bar.allowAllIPs: true` |
| `access.allowedHosts`, `access.requireHeader` | unchanged, now also apply to the console |

The old default allowed private networks. The new default, `"local"`, is loopback only. To keep the old behavior set `"bar": { "enabled": true, "access": [ "local", "private" ] }`.

## Per request overrides

`lensEnable()` and `lensDisable()` switch Lens for one request. The access rules still apply.
