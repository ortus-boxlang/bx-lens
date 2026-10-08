---
title: Security
order: 5
description: How Lens protects your data and what you must still do yourself.
icon: lucide:shield
---

# Security

!!! danger "Lens shows request internals"
    Lens exposes SQL, scopes, headers and stack traces. Keep the bar for development and test machines. If you turn the console on for a server that real users reach, follow [Running Lens in production](guides/production.md).

## Defaults

- Everything is off. `bar.enabled` and `console.enabled` are both `false`.
- The bar and the console each have their own access rule. Both default to `"local"`, which means loopback only (`127.0.0.1`, `::1`).
- The console also needs a password. Without one nobody can sign in.
- A caller that is not allowed gets nothing. The bar is not injected, and the console answers a plain 404, so it does not reveal that it exists.

## Access rules

| Setting | Default | Meaning |
|---|---|---|
| `bar.access` | `"local"` | `"local"`, `"all"`, or a list of exact IPs, CIDR ranges and the words `local` and `private`. |
| `bar.allowAllIPs` | `false` | Must be `true` for `bar.access: "all"`. Otherwise Lens falls back to loopback and logs an error. |
| `console.access` | `"local"` | Same format as `bar.access`. `"all"` is accepted and logs a warning. |
| `access.allowedHosts` | `[]` | Restrict to these `Host` header names. Empty means any host. Applies to both surfaces. |
| `access.requireHeader` | `""` | Require a request header: `"Name"` or `"Name=value"`. Applies to both surfaces. |

`private` means loopback plus 10/8, 172.16/12, 192.168/16, fc00::/7 and link-local. Only literal IP addresses are matched, Lens never does a DNS lookup.

```json title="Bar for a VPN range, console for loopback only, both behind a header"
{
	"modules": {
		"bxLens": {
			"settings": {
				"bar": { "enabled": true, "access": [ "local", "10.8.0.0/24" ] },
				"console": { "enabled": true, "password": "bxsecret:..." },
				"access": { "requireHeader": "X-Dev=1" }
			}
		}
	}
}
```

Behind a proxy or load balancer, the address Lens sees is the one the web server reports. Check what that is before you rely on an IP list.

## Two collection rules

- The **bar** collects only for callers allowed by `bar.access`. A caller who is not allowed is not tracked.
- The **console** collects every request on the server once it is enabled, because it shows production traffic. Only allowed, signed-in people see the data. Pair this with `collect.level: "light"`. See [Running Lens in production](guides/production.md).

## Console protection

The console is a single page with these protections.

| Protection | Detail |
|---|---|
| One password | Set `console.password` to a `bxsecret:` value. BoxLang decrypts it with the runtime seed at load time. The settings page shows only `set` or `not set`. |
| Sessions in memory | A restart signs everyone out. The cookie is `bxlens_session`: HttpOnly, SameSite=Strict, and Secure on HTTPS. |
| Timeouts | Idle timeout `console.sessionMinutes` (30), and a fixed 12 hour maximum. |
| Lockout | After `console.maxLoginAttempts` (5) wrong passwords, the address is locked for `console.lockoutMinutes` (5). Failures are counted in a 10 minute window. |
| Login header | A login must send `X-Lens-Login: 1`, so a form on another site cannot sign you in. |
| CSRF token | Every state change (`POST`) must send the session's `X-Lens-CSRF` token. |
| Strict CSP | Pages send `default-src 'none'` with `'self'` only for scripts, styles, images, connections and fonts, plus `frame-ancestors 'none'`. Responses also send `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer` and `Cache-Control: no-store`. |
| No outside requests | Alpine.js and the Phosphor icons are vendored, fonts are system fonts. The console runs on an air gapped network. |
| Fixed assets | Only `console.css`, `console.js` and `alpine.min.js` are served as assets. |
| Not tracked | Console requests never appear in Requests. |

Actions that change things (pause, resume, run, reload, saving the bar layout) are written to the log with the caller's address. Set `console.actions` to `false` to make the console read-only for tasks.

## Redaction

Lens masks values on the server before they reach the page. A key is masked when its name contains any `redact.keys` entry, compared case-insensitively. This covers scopes, request headers, response headers, query params, messages and panel data. The `Cookie` and `Authorization` headers are masked by default. In the console, sensitive keys in task definitions and in JVM flags are masked too.

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

## Security notes on your own pages

Lens also looks at your responses. Missing security headers and cookie flags show up as Notes on the Issues tab. See [Issues](panels/issues.md#security-notes).

## Checklist

1. Keep `bar.enabled` false in every shared or production config.
2. Turn the console on only with a `bxsecret:` password, a tight `console.access` list and HTTPS.
3. Use `collect.level: "light"` where real users send traffic.
4. Add your own sensitive keys to `redact.keys`.
5. Leave the `cookie`, `session`, `request`, `application` and `variables` scope dumps off unless you need them.
6. Set `console.actions` to `false` if nobody should pause or run tasks from the console.
