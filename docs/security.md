---
title: Security
order: 5
description: How Lens protects your data and what you must still do yourself.
icon: lucide:shield
---

# Security

!!! danger "Never enable Lens in production"
    Lens exposes SQL, scopes, headers and stack traces. Use it on development and test machines only.

## Defaults

- `enabled` is `false`. Nothing runs until you turn it on.
- When enabled, only loopback addresses (`127.0.0.1`, `::1`) and private networks may be tracked and see the bar.
- A disallowed caller is not tracked at all.

## Access rules

| Setting | Default | Meaning |
|---|---|---|
| `access.allowedIPs` | `["127.0.0.1", "::1"]` | Exact IPs or CIDR ranges. `"*"` allows everyone. Avoid it. |
| `access.allowPrivateNetworks` | `true` | Also allow 10/8, 172.16/12, 192.168/16, fc00::/7 and link-local. |
| `access.allowedHosts` | `[]` | Restrict to these `Host` header names. Empty means any host. |
| `access.requireHeader` | `""` | Require a request header: `"Name"` or `"Name=value"`. |

```json title="Only allow a VPN range and require a header"
{
	"modules": {
		"bxLens": {
			"settings": {
				"enabled": true,
				"access": {
					"allowedIPs": [ "127.0.0.1", "10.8.0.0/24" ],
					"allowPrivateNetworks": false,
					"requireHeader": "X-Dev=1"
				}
			}
		}
	}
}
```

## Redaction

Lens masks values on the server before they reach the page. A key is masked when its name contains any `redact.keys` entry, compared case-insensitively. This covers scopes, request headers, query params, messages and panel data. The `Cookie` and `Authorization` headers are masked by default.

```json title="Default redact settings"
{
	"redact": {
		"keys": [ "password", "pwd", "passwd", "token", "secret", "apikey", "api_key", "authorization", "cookie", "credential" ],
		"mask": "[redacted]"
	}
}
```

Add your own keys, for example `ssn`. Because matching is by substring, `token` also masks `apiToken` and `csrfToken`.

## Size caps

`limits.maxString` (2000), `limits.maxDepth` (4) and `limits.maxItems` (100) cap every value sent to the page. Each collector also has a `max` count. These caps keep a large request from flooding the page.

## Safe output

- Text from your app and from modules is always shown as text. The end to end suite checks that markup in messages, dumps and SQL never runs.
- JSON embedded in the page cannot be broken out of with a closing script tag.
- The bar adds one root element and makes no extra network requests.
- Panels contributed by modules and app code cannot supply HTML or script.

## Checklist

1. Keep `enabled` false in every shared or production config.
2. Keep the default `access` rules. Add hosts or IPs only when you need them.
3. Add your own sensitive keys to `redact.keys`.
4. Leave the `cookie`, `session`, `request`, `application` and `variables` scope dumps off unless you need them.
