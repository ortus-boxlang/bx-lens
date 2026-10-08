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
					"access": [ "10.8.0.0/24" ],
					"sessionMinutes": 15,
					"actions": false
				},
				"collect": { "level": "light" },
				"access": { "allowedHosts": [ "ops.example.com" ] },
				"history": { "maxRequests": 100 }
			}
		}
	}
}
```

## Step by step

1. **Encrypt the password.** Run `boxlang generatesecret "a long random password"` and paste the `bxsecret:` value into `console.password`. Pin the seed with `BOXLANG_SECURITY_SECRETSEED` if several machines share one config. Never commit a plain text password.
2. **Limit who can reach it.** Set `console.access` to a VPN range or a short list of IPs and CIDRs. Keep `"local"` if you tunnel in with SSH. Avoid `"all"`: it works, but Lens logs a warning. Add `access.allowedHosts` for the host name you use, and `access.requireHeader` if a proxy can add a secret header.
3. **Use HTTPS.** The session cookie is marked Secure only when the request is HTTPS, and the password travels in the login request. Terminate TLS in front of the runtime and make sure the runtime sees the request as secure. Check the cookie in your browser tools.
4. **Collect lightly.** `collect.level: "light"` skips request headers, bound query parameters, the caller of each query, scope contents, Java stack traces, and the `functions`, `logs` and `scopes` collectors. SQL text, timings and counts stay. Use `full` only on a machine you control.
5. **Turn the bar off.** Leave `bar.enabled` false. Developers do not need a strip injected into live pages.
6. **Decide on actions.** Set `console.actions` to `false` if nobody should run, pause or reload tasks from here. The Tasks page stays readable.
7. **Hide what you do not need.** `tabs.hide` removes pages, for example `["threads", "designer"]`.
8. **Size the history.** `history.maxRequests` is the memory you spend. Each request holds its full snapshot in memory.

## Check it

- Request `/~bxlens/index.bxm` from a machine that is not allowed. You get a plain 404.
- Request it from an allowed machine. You see the login page.
- The Settings page shows `console.password` as `set` and `collect.level` as `light`.
- The log shows `bx-lens ... active: bar=false, console=true, collect=light`.

## Know the limits

The console is for one server. It keeps recent requests in memory and loses them at a restart. For several nodes, history over time and alerts, use BX Insights.

See also [Security](../security.md#console-protection).
