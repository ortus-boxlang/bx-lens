---
title: Configuration
order: 3
description: Every BX Lens setting with its default and meaning.
icon: lucide:sliders-horizontal
---

# Configuration

Lens reads its settings from `boxlang.json` under `modules.bxLens.settings`. Lens parses them once at activation into an immutable config. Unknown keys log a warning.

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
	"injectPosition": "bodyEnd",
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
		"keys": [ "password", "pwd", "token", "secret", "apikey", "authorization", "cookie" ],
		"mask": "[redacted]"
	},
	"collectors": {
		"request": { "enabled": true },
		"timeline": { "enabled": true },
		"templates": { "enabled": true, "max": 300 },
		"functions": { "enabled": false, "max": 1000, "minMs": 0 },
		"queries": { "enabled": true, "max": 200, "slowMs": 25, "detectDuplicates": true, "detectNPlusOne": true, "includeParams": true },
		"http": { "enabled": true, "max": 100 },
		"exceptions": { "enabled": true, "max": 50 },
		"messages": { "enabled": true, "max": 200 },
		"timers": { "enabled": true, "max": 200 },
		"scopes": {
			"enabled": true, "form": true, "url": true, "cookie": false, "session": false,
			"request": false, "application": false, "maxDepth": 4, "maxBytes": 65536
		},
		"jvm": { "enabled": true, "threadDump": false },
		"cache": { "enabled": false },
		"logs": { "enabled": false, "minLevel": "INFO" },
		"session": { "enabled": false },
		"modules": { "enabled": true }
	}
}
```

## Core

| Key | Default | Description |
|---|---|---|
| `enabled` | `false` | Master switch. Lens does nothing when false. |
| `inject` | `true` | Inject the bar into HTML responses. Set false to render by hand with [`lensRender()`](guides/bifs.md#lensrender). |
| `injectPosition` | `"bodyEnd"` | Where the bar goes. `bodyEnd` places it before the last `</body>`. |
| `contentTypes` | `["text/html"]` | Response content types that get the bar. A type matches when the `Content-Type` starts with an entry. |
| `excludePaths` | `["/~bxlens/*", "/favicon.ico"]` | Paths that never get the bar. |

## `access`

| Key | Default | Description |
|---|---|---|
| `allowedIPs` | `["127.0.0.1", "::1"]` | Client addresses that may see the bar. |
| `allowPrivateNetworks` | `true` | Also allow private network ranges. |
| `allowedHosts` | `[]` | Extra host names that may see the bar. |
| `requireHeader` | `""` | When set, the request must carry this header. |

## `history`

| Key | Default | Description |
|---|---|---|
| `trackNonHtml` | `true` | Collect and store JSON, SSE, file and redirect requests too. They show no bar. |
| `header` | `"X-BxLens-Id"` | Response header that carries the stored request id. |
| `maxRequests` | `50` | Size of the in-memory ring buffer. The oldest request is recycled when full. |

## `ui`

| Key | Default | Description |
|---|---|---|
| `theme` | `"auto"` | `auto`, `light` or `dark`. |
| `startOpen` | `false` | Open the panel on page load instead of the collapsed strip. |
| `autoOpenOnException` | `true` | Open the relevant tab when an exception is caught or thrown. |
| `defaultTab` | `"timeline"` | Tab shown first. |
| `height` | `360` | Initial panel height in pixels. |
| `allowDetach` | `true` | Allow the pop-out window. |
| `hotkey` | ``"Ctrl+`"`` | Toggle shortcut. |

## `thresholds`

| Key | Default | Description |
|---|---|---|
| `slowRequestMs` | `500` | Requests slower than this turn the strip amber. |
| `slowQueryMs` | `25` | Queries slower than this raise an issue. |
| `slowTemplateMs` | `100` | Templates slower than this raise an issue. |
| `nPlusOneMin` | `3` | Repeats of the same query shape needed to flag an N+1. |

## `editor`

| Key | Default | Description |
|---|---|---|
| `linkPattern` | `"vscode://file/{path}:{line}"` | URL pattern for open-in-editor links. |
| `remoteBase` | `""` | Path prefix on the server, for example inside a container. |
| `localBase` | `""` | Path prefix on your machine that replaces `remoteBase`. |

Use `remoteBase` and `localBase` together when the app runs in Docker or on a remote host.

```json title="Docker path mapping"
{
	"editor": { "remoteBase": "/app", "localBase": "/Users/me/projects/shop" }
}
```

## `redact`

| Key | Default | Description |
|---|---|---|
| `keys` | `["password", "pwd", "token", "secret", "apikey", "authorization", "cookie"]` | Key names to redact on scopes, headers, params and messages. |
| `mask` | `"[redacted]"` | Replacement text. |

## `collectors`

Each collector powers one panel and has an `enabled` flag. Most also have a `max` that caps stored items per request.

| Collector | Key | Default | Description |
|---|---|---|---|
| `request` | `enabled` | `true` | Request details. |
| `timeline` | `enabled` | `true` | The waterfall. |
| `templates` | `enabled` | `true` | Template and include tree. |
| | `max` | `300` | Max template entries. |
| `functions` | `enabled` | `false` | Function calls. Opt in, this is a hot path. |
| | `max` | `1000` | Max function entries. |
| | `minMs` | `0` | Skip calls faster than this. |
| `queries` | `enabled` | `true` | SQL queries. |
| | `max` | `200` | Max query entries. |
| | `slowMs` | `25` | Slow query threshold for this collector. |
| | `detectDuplicates` | `true` | Flag identical queries. |
| | `detectNPlusOne` | `true` | Flag N+1 patterns. |
| | `includeParams` | `true` | Show bound parameters. |
| `http` | `enabled` | `true` | Outgoing HTTP calls. |
| | `max` | `100` | Max HTTP entries. |
| `exceptions` | `enabled` | `true` | Thrown and caught exceptions. |
| | `max` | `50` | Max exceptions. |
| `messages` | `enabled` | `true` | Messages and dumps. |
| | `max` | `200` | Max messages. |
| `timers` | `enabled` | `true` | Timers and measures. |
| | `max` | `200` | Max timers. |
| `scopes` | `enabled` | `true` | Scope viewer. |
| | `form`, `url` | `true` | Include these scopes. |
| | `cookie`, `session`, `request`, `application` | `false` | Include these scopes. Opt in. |
| | `maxDepth` | `4` | Max nesting depth shown. |
| | `maxBytes` | `65536` | Max serialized size. |
| `jvm` | `enabled` | `true` | JVM and runtime panel. |
| | `threadDump` | `false` | Allow thread dumps. Opt in. |
| `cache` | `enabled` | `false` | Cache panel. Opt in. |
| `logs` | `enabled` | `false` | Log messages. Opt in. |
| | `minLevel` | `"INFO"` | Lowest level to collect. |
| `session` | `enabled` | `false` | Session details. Opt in. |
| `modules` | `enabled` | `true` | Loaded modules panel. |

!!! note "Two slow query settings"
    The spec lists both `thresholds.slowQueryMs` and `collectors.queries.slowMs`, each defaulting to `25`. Set both if you change one.

## Per request overrides

- `lensEnable()` and `lensDisable()` switch Lens for one request.
- An `X-BxLens: on|off` cookie or header does the same. Both honor the `access` rules.
