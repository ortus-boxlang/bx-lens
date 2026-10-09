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
- Heap dumps (`console.allowHeapDump`, BoxLang+ only) and AI calls to a model (`ai.enabled`) are off. See [Heap dumps](#heap-dumps) and [AI data flow](#ai-data-flow). The disk store writes only with BoxLang+ or a trial, see [Licensing](licensing.md).
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

Behind a proxy or load balancer, see [Behind a proxy](#behind-a-proxy).

## Behind a proxy

With a proxy or load balancer in front, the direct connection comes from the proxy, not from the user. Lens can take the client address from a header so `console.access`, `bar.access` and the login lockout use the real address.

| Setting | Default | Meaning |
|---|---|---|
| `access.trustProxyHeader` | `true` | Use the header at all. |
| `access.proxyHeader` | `"X-Forwarded-For"` | The header to read. |
| `access.proxyPeers` | `"private"` | Which direct connections may set the header: `private`, `local`, or a list of IPs and CIDR ranges. |

The rules:

- The header is believed only when the direct connection comes from one of `proxyPeers`. From any other address the header is ignored, so a user cannot pick an address by sending the header.
- With a list of addresses in the header, Lens reads it from the right and uses the first address that is not a trusted proxy.
- A header can never turn a remote peer into a loopback caller. If the header names `127.0.0.1` and the peer is not loopback, Lens keeps the peer address.
- The `X-Forwarded-Proto` header is believed under the same rule, when Lens decides whether a request is HTTPS.

The default `private` trusts any proxy on a private network. If other machines on that network can reach the runtime directly, list your proxy addresses in `access.proxyPeers`. If no proxy is in front, set `access.trustProxyHeader` to `false`.

## Two collection rules

- The **bar** collects only for callers allowed by `bar.access`. A caller who is not allowed is not tracked.
- The **console** collects every request on the server once it is enabled, because it shows production traffic. Only allowed, signed-in people see the data. Pair this with `collect.level: "light"`. See [Running Lens in production](guides/production.md).

## Console protection

The console is a single page with these protections.

| Protection | Detail |
|---|---|
| Passwords | Set `console.password` (and optionally `console.viewerPassword`) to a `bxsecret:` value. BoxLang decrypts it with the runtime seed at load time. The settings page shows only `set` or `not set`. |
| Sessions in memory | A restart signs everyone out. The cookie is `bxlens_session`: HttpOnly, SameSite=Strict, and Secure on HTTPS. |
| Timeouts | Idle timeout `console.sessionMinutes` (30), and a fixed 12 hour maximum. |
| Lockout | After `console.maxLoginAttempts` (5) wrong passwords, the address is locked for `console.lockoutMinutes` (5). Failures are counted in a 10 minute window. |
| Login header | A login must send `X-Lens-Login: 1`, so a form on another site cannot sign you in. |
| CSRF token | Every state change (`POST`) must send the session's `X-Lens-CSRF` token. |
| Strict CSP | Pages send `default-src 'none'` with `'self'` only for scripts, styles, images, connections and fonts, plus `frame-ancestors 'none'`. Responses also send `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer` and `Cache-Control: no-store`. |
| No outside requests | Alpine.js and the Phosphor icons are vendored, fonts are system fonts. The console runs on an air gapped network. |
| Fixed assets | Only `console.css`, `console.js` and `alpine.min.js` are served as assets. |
| Not tracked | Console requests never appear in Requests. |
| HTTPS | `console.requireHttps` refuses plain HTTP, see [HTTPS](#https). |
| Roles | Admin and viewer, see [Roles](#roles). |
| Audit log | Logins and changes are written to `bxlens-audit.log`, see [Audit log](#audit-log). |

Set `console.actions` to `false` to turn off task actions, and `console.readOnly` to `true` to refuse every change from the console.

## Roles

The password decides the role. `console.password` gives the admin role. `console.viewerPassword` (optional, ignored when no admin password is set) gives the viewer role.

| | Viewer | Admin |
|---|---|---|
| See Overview, Requests, In flight, Queries, Errors, Reports, Executors, Tasks, Datasources, Caches (statistics and key names), Modules and Settings | yes | yes |
| Read the Logs page, the Environment page, the System page, the Threads page and thread stacks (`/api/threads`) | no | yes |
| See the values of `console.access`, `access.proxyPeers` and `console.overridesFile` in Settings | no | yes |
| Change settings | no | yes |
| Task actions and saving the bar layout (BoxLang+ or a trial) | no | yes |
| Cache evict, reap and clear (BoxLang+ or a trial) | no | yes |
| Reset Queries, Errors and Reports, Run GC | no | yes |
| Test a datasource connection | no | yes |
| Call a model (Explain with AI, Ask) | no | yes |
| Download a thread dump | no | yes |
| Download a heap dump, a log file or the diagnostic bundle (BoxLang+ or a trial) | no | yes |
| Read a cache value (BoxLang+ or a trial) | no | yes |
| Copy a prompt | yes | yes |

A viewer who sends such a request gets a 403, and the attempt goes to the audit log as `denied`. The list of routes only admins can use is `ADMIN_ONLY` in `ConsoleRouter`, plus every request that changes state.

!!! warning "A viewer still sees a lot"
    A viewer reads request data, queries, scope snapshots and key names of caches. Logs, thread stacks, the environment and system details are for admins. Request data can still be sensitive, so treat the viewer password as sensitive.

### Brute force and the audit log

Failed logins are counted per client in two windows, one for each role. A wrong password counts against both, because it may have been a guess at either. A sign in as a viewer clears only the viewer window, so the viewer password can never be used to give the admin window its attempts back. IPv6 clients are counted by their /64 network. Sessions (200) and failure records (5000) are capped and pruned on every login. A live stream does not keep a session from going idle.

Every field in an audit line is stripped of control characters and cut to a length, so a client cannot forge a line. Refusals are audited too: `denied` (viewer), `denied.csrf`, `denied.readonly`, `denied.plus`, `denied.actions` and `denied.access` (a caller the console answers with 404, at most one line a minute per address). Reading a log file (`logfile.read`) and the environment (`environment.read`) are audited. A search term is logged as its length only.

### Host names and DNS rebinding

A guard whose rule is exactly `local` accepts only the Host names `localhost`, `127.0.0.1` and `[::1]`, unless `access.allowedHosts` is set. Add your own name there to reach the bar or console under it.

`console.readOnly` is stricter: it refuses every change for both roles. Heap dumps (own switch, BoxLang+), the datasource connection test and the AI calls are not stopped by it. See [Console settings](console/settings.md#view-only).

## HTTPS

The password and everything on screen travel over the connection, so use HTTPS.

- `console.requireHttps: true` answers a plain HTTP request with a 403. Loopback callers are exempt, so a local tunnel still works.
- With the option off, the console shows a warning banner on every page when a non-loopback caller uses plain HTTP.
- The session cookie is marked Secure when the request is HTTPS.
- Behind a proxy that ends TLS, the proxy must send `X-Forwarded-Proto: https` from a trusted peer, or the runtime must see the request as secure. See [Behind a proxy](#behind-a-proxy).

## Audit log

Lens writes a line for every login, failed login, lockout, logout, denied attempt and change to its own log, `bxlens-audit.log`, through the BoxLang logging service. It is in the BoxLang logs directory, so you can read it on the [Logs](console/logs.md) page. A line looks like this:

```text
event=settings.change role=admin ip=10.0.0.12 keys={thresholds.slowQueryMs=50}
```

The events are:

| Group | Events |
|---|---|
| Access | `login.ok`, `login.fail`, `login.locked`, `logout`, `denied` |
| Settings | `settings.change`, `settings.reset`, `bar.layout`, `bar.reset` |
| Tasks | `task.<action>`, for example `task.run` |
| Downloads | `threads.dump`, `logfile.download`, `bundle.download`, `heapdump.download`, `cache.value` |
| Actions | `gc.run`, `heapdump.start`, `heapdump.refused`, `heapdump.discard`, `cache.clear`, `cache.evict`, `cache.reap`, `datasource.test`, `queries.reset`, `errors.reset`, `reports.reset` |
| AI | `ai.explain`, `ai.ask` (size and provider only, never the prompt or the answer) |

Passwords and keys are never written. A line is cut at 500 characters and line breaks are removed. Lens keeps no other record and does not rotate or trim the file itself.

## Heap dumps

Heap dumps need BoxLang+ or a trial. On Free the server answers 403. A heap dump is a copy of everything in the JVM's memory: passwords, session data, keys, personal data. Lens cannot redact it.

- It is off. Set `console.allowHeapDump` to `true` in `boxlang.json` to turn it on, and turn it off again when you are done.
- Only admins can take and download one. A confirmation is required, and `console.readOnly` does not block it.
- The file is written to a private folder in the temporary directory, one at a time, after a disk space check. It is deleted 5 minutes after the download, or 10 minutes after it was written if nobody downloads it. A restart of the module deletes it too.
- Every step is in the audit log.

Download over HTTPS only, keep the file in a safe place, and delete it when you are done. See [System and Threads](console/system-and-threads.md#run-gc-and-heap-dumps).

## AI data flow

AI help is optional and has three levels. Lens never calls a model unless an admin sets `ai.enabled`, the server has BoxLang+ or a trial, and the `bx-ai` module that ships inside Lens is present.

| Level | What leaves the server |
|---|---|
| Copy prompt | Nothing. The text goes to your clipboard. |
| Ask ChatGPT, Ask Claude | Nothing from the server. Your browser opens the site and you paste the prompt. |
| Explain with AI, Ask (through `bx-ai`) | The prompt goes to the provider you configured. |

What a prompt holds is limited to data Lens has already redacted: stack frames, SQL text without parameter values, query strings with secret parameters hidden, short messages, and a summary of server health. Credentials in URLs and secret-looking parameters are removed from the whole prompt, and a prompt is cut at 8000 characters. Passwords, parameter values, scope contents and the configuration are not included.

Two limits you should know. Exception messages and SQL text are included as the application produced them, so data your code puts into a message is in the prompt. The question you type on the Ask Lens page is sent as you typed it.

A local provider, such as Ollama, keeps the prompt inside your network. A hosted provider receives it, so check its terms. The API key is a `bxsecret:` value and is never shown. Calls are limited to 10 per minute and one at a time, and each is in the audit log without its content. See [Ask Lens and AI help](console/ask-lens-and-ai.md).

## The diagnostic bundle

The bundle needs BoxLang+ or a trial. On Free the server answers 403. It is a zip for support tickets. It holds a thread dump, the environment (configuration, modules, JVM arguments, environment variables, system properties), the system, executor, task, datasource and cache data, and the Lens settings. It holds no request data and no log files.

Secrets are hidden by name, not by value. A variable, property or setting whose name looks secret (password, token, key, secret, credential, cookie, session and similar) shows as `[hidden]`, URL credentials are removed and `bxsecret:` values show as `[encrypted]`. A secret kept under a harmless name is not hidden. Look inside the zip before you send it. Only admins can download it, and each download is in the audit log.

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

The Logs page does not redact. The Environment page and the diagnostic bundle hide secrets by name with a fixed check, described under [The diagnostic bundle](#the-diagnostic-bundle), not with `redact.keys`.

## Size caps

`limits.maxString` (2000), `limits.maxDepth` (4) and `limits.maxItems` (100) cap every value sent to the page. Each collector also has a `max` count. These caps keep a large request from flooding the page.

## Safe output

- Text from your app and from modules is always shown as text. The end to end suite checks that markup in messages, dumps and SQL never runs.
- JSON embedded in the page cannot be broken out of with a closing script tag.
- The bar adds one root element and makes no extra network requests.
- Panels contributed by modules and app code cannot supply HTML or script.

## Security notes on your own pages

Lens also looks at your responses. Missing security headers and cookie flags show up as Notes in the console request detail. See [Issues](console/issues.md#security-notes).

## Checklist

1. Keep `bar.enabled` false in every shared or production config.
2. Turn the console on only with a `bxsecret:` password, a tight `console.access` list and HTTPS (`console.requireHttps: true`).
3. Use `collect.level: "light"` where real users send traffic.
4. Add your own sensitive keys to `redact.keys`.
5. Leave the `cookie`, `session`, `request`, `application` and `variables` scope dumps off unless you need them.
6. Set `console.actions` to `false` if nobody should pause or run tasks from the console, and `console.readOnly` to `true` if nobody should change anything.
7. Give people who only look the viewer password.
8. Behind a proxy, list it in `access.proxyPeers`.
9. Leave `console.allowHeapDump` off.
10. Keep `ai.enabled` off, or use a local provider.
11. Read `bxlens-audit.log` now and then.
