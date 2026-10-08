---
title: Console
order: 1
description: The standalone BX Lens console for one server, with login, live requests, executors, tasks and more.
icon: lucide:layout-dashboard
---

# Console

The console is a standalone page for one server. It shows live requests, executor health, scheduled tasks, JVM numbers and threads, and it lets you design the bar. It is off by default.

![The console overview](../assets/screenshots/console-overview.png)

## Turn it on

```json title="boxlang.json"
{
	"modules": {
		"bxLens": {
			"settings": {
				"console": {
					"enabled": true,
					"password": "bxsecret:AbCdEf123...==",
					"access": "local"
				}
			}
		}
	}
}
```

Restart the runtime. The console needs `console.enabled` and a password. If the password is empty or cannot be decrypted, Lens logs an error and an allowed caller sees a setup page (HTTP 503) instead of a login.

### Make the password

Store the password as an encrypted `bxsecret:` value. BoxLang decrypts any config value with that prefix at load time, using the runtime's secret seed (BoxLang 1.17 or newer).

```bash
boxlang generatesecret "a long random password"
# bxsecret:AbCdEf123...==
```

Paste the result into `console.password`. Use the same seed on every machine that shares the config. A plain text password also works but is a bad idea in a file you commit.

## Open it

The console lives at:

```text
/~bxlens/index.bxm
```

This is a public mapping named `~bxlens`, the same pattern as `/~bxai` in bx-ai. A bare `/~bxlens/` is not served by MiniServer (it answers 404), so always use the `index.bxm` URL. Every other route is under it, for example `/~bxlens/index.bxm/stream`.

## Sign in

One password, no user names.

![The console login](../assets/screenshots/console-login.png)

A wrong password says so and counts the attempt. After `console.maxLoginAttempts` wrong tries the address is locked out for `console.lockoutMinutes`.

![A wrong password](../assets/screenshots/console-login-error.png)

Sessions live in memory. They end after `console.sessionMinutes` idle, 12 hours after login at the latest, or at a restart.

## Pages

| Page | What it is for |
|---|---|
| Overview | Requests per second, error rate, p95 and median time, queries per request, slowest routes and what needs attention. |
| Requests | Every request in memory, with detail. See below. |
| [Executors](executors.md) | Live health of every executor. |
| [Tasks](tasks.md) | Schedulers and scheduled tasks, with Run now, pause, resume and reload. |
| [System](system-and-threads.md) | CPU, memory, garbage collection, classes, disks and runtime details. |
| [Threads](system-and-threads.md#threads) | Thread viewer and thread dump. |
| [Bar designer](bar-designer.md) | Choose and order the tabs of the bar. |
| [Settings](settings.md) | The effective settings, read-only. |

Hide a page with `tabs.hide`, or turn its collector off. Settings is always there.

### Requests

The list shows time, method, URL, status, duration, SQL count and issue count, newest first. Filter by All, Errors, Slow or JSON, or by URL text. Pick a row to see the detail: status, timings, issues, the slow request sample if there is one, and a timeline waterfall.

![The Requests page](../assets/screenshots/console-requests.png)

From the detail you can copy the request as JSON, download it as a JSON file, or copy it as a cURL command (redacted headers are left out). Each request has a deep link, `/~bxlens/index.bxm#requests/{id}`, which the bar's More menu uses. History is an in-memory ring buffer of `history.maxRequests` (default 50). A request that was recycled shows "That request has been recycled".

The console never lists its own requests.

## Live data

One Server-Sent Events stream per browser (`GET /~bxlens/index.bxm/stream`) pushes requests, executors, tasks and system numbers once a second. A newer stream from the same session replaces the older one, and a stream ends after five minutes and the browser reconnects by itself. The header shows `live 3s` or `paused`. If the stream is unavailable (no `EventSource`, or `console.maxStreams` is reached), the page polls every 3 seconds instead. Threads refresh every 5 seconds while open, because a thread dump briefly pauses the JVM.

## Security model

The console is built for a hostile network, within reason.

- Callers outside `console.access`, `access.allowedHosts` or `access.requireHeader` get a plain 404.
- Strict Content-Security-Policy and no request to any other site. Alpine.js and the Phosphor icons are vendored and fonts are system fonts, so it works air gapped.
- HttpOnly, SameSite=Strict session cookie (Secure on HTTPS), a CSRF header on every state change, and a custom header on login.
- Per-address lockout.

Read the full list in [Security](../security.md#console-protection), and use [Running Lens in production](../guides/production.md) before you expose it.

## What the console is not

It is for ONE server. It keeps the last `history.maxRequests` requests in memory and nothing on disk (apart from the saved bar layout). For clusters, history over time and alerting, use the separate BX Insights product. See [Roadmap](../project/roadmap.md#relation-to-bx-insights).
