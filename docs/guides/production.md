---
title: Running Lens in production
order: 3
description: Turn the console on for a live server with a password, an access list, HTTPS and light collection.
icon: lucide:server
---

# Running Lens in production

The bar is a development tool. The console can run on a live server if you set it up with care. This guide gives a safe starting point.

## What changes in production

When the console is on, Lens collects every request on the server, not only those from allowed callers, so the console can show real traffic. Only people who pass the access rule and sign in can read it. That makes three choices important: who may reach it, what is collected, and what the bar does.

## A safe starting point

```json title="boxlang.json"
{
	"modules": {
		"bxLens": {
			"settings": {
				"bar": { "enabled": false },
				"console": {
					"enabled": true,
					"password": "bxsecret:AbCdEf123...==",
					"viewerPassword": "bxsecret:GhIjKl456...==",
					"requireHttps": true,
					"access": [ "10.8.0.0/24" ],
					"sessionMinutes": 15,
					"actions": false,
					"readOnly": true,
					"allowHeapDump": false
				},
				"collect": { "level": "light" },
				"access": {
					"allowedHosts": [ "ops.example.com" ],
					"trustProxyHeader": true,
					"proxyHeader": "X-Forwarded-For",
					"proxyPeers": [ "10.0.1.5" ]
				},
				"store": { "dir": "/var/lib/boxlang/lens-data" },
				"ai": { "enabled": false },
				"history": { "maxRequests": 100 }
			}
		}
	}
}
```

`readOnly: true` and `actions: false` suit a console that is only for looking. Take them out when your operators need to change settings or run tasks from the console.

## Step by step

1. **Encrypt the passwords.** Run `boxlang generatesecret "a long random password"` and paste the `bxsecret:` value into `console.password`. Do the same, with another password, for `console.viewerPassword`. Pin the seed with `BOXLANG_SECURITY_SECRETSEED` if several machines share one config. Never commit a plain text password.
2. **Give people the right role.** Operators who only look use the viewer password. A viewer cannot change anything, cannot download dumps, logs or the bundle, and does not get the Logs, Environment, System and Threads pages, but can still read request data and queries, so keep the password private. See [Roles](../security.md#roles).
3. **Limit who can reach it.** Set `console.access` to a VPN range or a short list of IPs and CIDRs. Keep `"local"` if you tunnel in with SSH. Avoid `"all"`: it works, but Lens logs a warning. Add `access.allowedHosts` for the host name you use (a `local` rule otherwise accepts only `localhost`, `127.0.0.1` and `[::1]` as Host names), and `access.requireHeader` if a proxy can add a secret header.
4. **Use HTTPS and require it.** Set `console.requireHttps` to `true`. Terminate TLS in front of the runtime and make sure the runtime sees the request as secure: either it reports HTTPS itself, or your proxy sends `X-Forwarded-Proto: https`. Check the cookie in your browser tools.
5. **Say which proxies to trust.** Behind a proxy or load balancer, set `access.proxyPeers` to the addresses of your proxies, not the whole private network, so only they can name the client address. Without a proxy, set `access.trustProxyHeader` to `false`. See [Behind a proxy](../security.md#behind-a-proxy).
6. **Collect lightly.** `collect.level: "light"` skips request headers, bound query parameters, the caller (template and line) of queries, HTTP calls and transactions, the BoxLang frames of exceptions, scope contents, Java stack traces, and the `functions`, `logs`, `scopes` and `bifs` collectors. SQL text, timings and counts stay. It is the default. Non HTML requests are not kept in the history (`history.trackNonHtml`), and the history is built lazily, so a request nobody opens costs no snapshot. Use `full` only on a machine you control.
7. **Turn the bar off.** Leave `bar.enabled` false. Developers do not need a strip injected into live pages.
8. **Decide on changes.** Set `console.actions` to `false` if nobody should run, pause or reload tasks from here (these actions also need BoxLang+). Set `console.readOnly` to `true` if nobody should change anything from the console. The pages stay readable.
9. **Keep heap dumps off.** Heap dumps need BoxLang+ or a trial. Leave `console.allowHeapDump` at `false`. A heap dump holds every secret in memory. Turn it on for the time you need it, then off. See [Heap dumps](../security.md#heap-dumps).
10. **Decide on AI.** Leave `ai.enabled` false, or use a local provider so prompts stay in your network. See [AI data flow](../security.md#ai-data-flow).
11. **Put the store on a volume.** With BoxLang+ or a trial, errors and reports are saved to `store.dir`. In a container, mount a volume there, or the data is lost when the container is replaced. Set `store.retentionHours` and `store.maxMB` to what you want to keep. See [Errors and Reports](../console/errors-and-reports.md#the-disk-store).
12. **Hide what you do not need.** `tabs.hide` removes pages, for example `["threads", "designer", "environment"]`.
13. **Size the history.** `history.maxRequests` is the memory you spend. Each request holds its full snapshot in memory. On Free the history is capped at 25.
14. **Watch the audit log.** `bxlens-audit.log` in the logs directory records logins, failed logins, denied attempts and every change. Read it on the Logs page or ship it with your other logs.

## The console URL

`/~bxlens/index.bxm` always works. Lens also rewrites `/~bxlens` and `/~bxlens/` (with the trailing slash) to it, in `onWebExecutorRequest`. Nothing else in the public folder becomes reachable that way.

On MiniServer the request must also pass the pass predicate, or MiniServer serves it as a static path and never hands it to BoxLang. Add the console path to the default predicate. Set the environment variable `BOXLANG_PASS_PREDICATE` to this exact value:

```text
regex( '^(/.+?\.cfml|/.+?\.cf[cms]|.+?\.bx[ms]{0,1})(/.*)?$' ) or regex( '^/~bxlens/?$' )
```

or set the same string as `passPredicate` in `miniserver.json` (in JSON, write each backslash twice). The first `regex` is the default MiniServer predicate and the second one lets the console path through. The harness does this in `harness/start.sh`.

MiniServer welcome files do not apply to module mappings, and the list of welcome files is not configurable in core. That is why Lens does the rewrite itself.

On CommandBox or a servlet container this was not tested. The trailing slash form works only if the path reaches the BoxLang servlet, which depends on the server's own mappings. Use `/~bxlens/index.bxm` there.

## Check it

- Request `/~bxlens/index.bxm` from a machine that is not allowed. You get a plain 404.
- Request it over plain HTTP from an allowed non-loopback machine. You get a 403 with `HTTPS is required for the console`.
- Request it from an allowed machine over HTTPS. You see the login page.
- Sign in with the viewer password and try to change a setting. The fields are disabled, and a direct call gets a 403.
- The Settings page shows the passwords as `set`, `collect.level` as `light` and `console.readOnly` as true.
- The log shows `bx-lens ... active: bar=false, console=true, collect=light`.
- After a restart, the Reports page still shows "Since first install" (needs BoxLang+ or a trial).

## Know the limits

The console is for one server. It keeps recent requests, query statistics and in-flight data in memory and loses them at a restart. With BoxLang+ or a trial, errors and reports survive restarts. For several nodes, history over time and alerts, use BX Insights.

See also [Security](../security.md#console-protection).
