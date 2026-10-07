---
title: Configuration
order: 3
description: Every BX Lens setting with its default and meaning.
icon: lucide:sliders-horizontal
---

# Configuration

Lens reads its settings from `boxlang.json` under `modules.bxLens.settings`. You only set what you want to change. The schema and defaults come from the module's `ModuleConfig.bx`.

```json title="boxlang.json"
{
	"modules": {
		"bxLens": {
			"settings": {
				"enabled": true,
				"thresholds": { "slowQueryMs": 50 }
			}
		}
	}
}
```

## Defaults

```json title="Full default settings"
{
	"enabled": false,
	"access": {
		"allowedIPs": [ "127.0.0.1", "::1" ],
		"allowPrivateNetworks": true,
		"allowedHosts": [],
		"requireHeader": ""
	},
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
		"modules": { "enabled": true }
	}
}
```

## Core

| Key | Default | Description |
|---|---|---|
| `enabled` | `false` | Master switch. Lens does nothing when false. Never turn it on for a public site. |
| `inject` | `true` | Inject the bar before `</body>` on HTML responses. Set false and call [`lensRender()`](guides/bifs.md#lensrender) to place it yourself. |
| `contentTypes` | `["text/html"]` | Response content types that get the bar. |
| `excludePaths` | `["/~bxlens/*", "/favicon.ico"]` | Paths that never get the bar. |

## `access`

| Key | Default | Description |
|---|---|---|
| `allowedIPs` | `["127.0.0.1", "::1"]` | Exact IPs, CIDR ranges, or `"*"` for everyone (dangerous). |
| `allowPrivateNetworks` | `true` | Also allow private ranges: 10/8, 172.16/12, 192.168/16, fc00::/7 and link-local. |
| `allowedHosts` | `[]` | Restrict to these `Host` header names. Empty means any host. |
| `requireHeader` | `""` | Require a request header: `"Name"` or `"Name=value"`. Empty means none. |

## `history`

| Key | Default | Description |
|---|---|---|
| `trackNonHtml` | `true` | Record JSON, SSE, file and redirect requests too. They show no bar. |
| `header` | `"X-BxLens-Id"` | Response header that carries the stored request id. |
| `maxRequests` | `50` | Size of the in-memory ring buffer. The oldest request is recycled when full. |

## `ui`

| Key | Default | Description |
|---|---|---|
| `theme` | `"auto"` | `auto` follows the OS. Or `dark`, `light`. |
| `startOpen` | `false` | Start with the panel open instead of the collapsed strip. |
| `autoOpenOnException` | `true` | Open the panel on Issues when an exception was caught. |
| `defaultTab` | `"timeline"` | Tab shown first. |
| `height` | `360` | Initial panel height in pixels. |
| `allowDetach` | `true` | Allow the floating detached window. |
| `hotkey` | ``"Ctrl+`"`` | Toggle shortcut. Modifiers: Ctrl, Alt, Shift, Meta. |

## `thresholds`

| Key | Default | Description |
|---|---|---|
| `slowRequestMs` | `500` | Requests slower than this raise an issue. |
| `slowQueryMs` | `25` | Queries slower than this warn. At 4 times this value they are critical. |
| `slowTemplateMs` | `100` | Templates and functions with a self time above this raise an issue. |
| `nPlusOneMin` | `3` | Runs of the same SQL needed to flag an N+1. |

## `editor`

| Key | Default | Description |
|---|---|---|
| `linkPattern` | `"vscode://file/{path}:{line}"` | Click-to-editor link. `{path}` and `{line}` are replaced. |
| `remoteBase` | `""` | Path prefix on the server, for example inside a container. |
| `localBase` | `""` | Path prefix on your machine that replaces `remoteBase`. |

```json title="Docker path mapping"
{
	"editor": { "remoteBase": "/app", "localBase": "/Users/me/projects/shop" }
}
```

## `redact`

| Key | Default | Description |
|---|---|---|
| `keys` | `["password", "pwd", "passwd", "token", "secret", "apikey", "api_key", "authorization", "cookie", "credential"]` | A key is masked when its name contains any of these, ignoring case. |
| `mask` | `"[redacted]"` | Replacement text. |

## `limits`

Caps for any value sent to the page.

| Key | Default | Description |
|---|---|---|
| `maxString` | `2000` | Longest string. |
| `maxDepth` | `4` | Deepest nesting. |
| `maxItems` | `100` | Most items in a collection. |

## `collectors`

Each collector powers one or more panels and has an `enabled` flag. Most also have a `max` that caps stored items per request.

| Collector | Key | Default | Description |
|---|---|---|---|
| `templates` | `enabled` | `true` | Template and include tree. |
| | `max` | `300` | Max template entries. |
| `functions` | `enabled` | `false` | Function calls. Off by default because it is a hot path. |
| | `max` | `1000` | Max function entries. |
| | `minMs` | `0` | Skip calls faster than this. |
| `queries` | `enabled` | `true` | SQL queries. |
| | `max` | `200` | Max query entries. |
| | `includeParams` | `true` | Show bound parameters. |
| | `captureCaller` | `true` | Record the template and line that ran each query. |
| `http` | `enabled`, `max` | `true`, `100` | Outgoing HTTP calls. |
| `exceptions` | `enabled`, `max` | `true`, `50` | Thrown and caught exceptions. |
| `messages` | `enabled`, `max` | `true`, `200` | Messages and dumps. |
| `timers` | `enabled`, `max` | `true`, `200` | Timers and measures. |
| `transactions` | `enabled`, `max` | `true`, `50` | Transactions, shown as spans on the Timeline. |
| `logs` | `enabled`, `max` | `false`, `200` | Log messages, shown in Messages. |
| `scopes` | `enabled` | `true` | Scope viewer. |
| | `url`, `form` | `true` | Include these scopes. |
| | `cookie`, `session`, `request`, `application`, `variables` | `false` | Include these scopes. Opt in. |
| `jvm` | `enabled` | `true` | Runtime panel. |
| `cache` | `enabled` | `true` | Cache panel. |
| `modules` | `enabled` | `true` | Modules panel. |

## Contributor setting

`dev.reloadAssets` serves the UI files from disk on every request, so edits show on refresh. The harness config turns it on, and `DEV=1` links the harness to the source asset files. See [Development](project/contributing.md#the-harness). Leave it off otherwise.

## Per request overrides

`lensEnable()` and `lensDisable()` switch Lens for one request. The `access` rules still apply.
