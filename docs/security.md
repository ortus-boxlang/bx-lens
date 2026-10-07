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
- When enabled, only loopback addresses (`127.0.0.1`, `::1`) and private networks may see the bar. Control this with `access.allowedIPs`, `access.allowPrivateNetworks`, `access.allowedHosts` and `access.requireHeader`.
- A per-request override (`lensEnable()`, or the `X-BxLens: on|off` cookie or header) works only when `access` allows the caller.

## Redaction

Lens redacts values on the server, before it serializes anything. This covers scopes, headers, params and messages. Any key that matches an entry in `redact.keys` is replaced with `redact.mask`.

```json title="boxlang.json"
{
	"modules": {
		"bxLens": {
			"settings": {
				"redact": {
					"keys": [ "password", "pwd", "token", "secret", "apikey", "authorization", "cookie", "ssn" ],
					"mask": "[redacted]"
				}
			}
		}
	}
}
```

## Size caps

Each collector has a `max` count, and scopes also have `maxDepth` and `maxBytes`. These caps keep a large request from flooding the page.

## Safe output

- The UI uses the Alpine CSP build, so it needs no `eval`. Assets come from the module under `/~bxlens/`, and the bar uses no inline handlers.
- JSON embedded in the page escapes `<`, `>`, `&`, U+2028 and U+2029 as unicode escapes.
- Text from your app and from modules is always escaped. Tier 1 extensions cannot supply HTML or script.

## Dumps

Heap dump and thread dump are off. Thread dump sits behind `collectors.jvm.threadDump`. Heap dump needs a separate opt-in setting named `allowHeapDump`. Neither writes outside the temp directory.

## Checklist

1. Keep `enabled` false in every shared or production config.
2. Keep the default `access` rules. Add hosts or IPs only when you need them.
3. Add your own sensitive keys to `redact.keys`.
4. Leave the `cookie`, `session`, `request` and `application` scope dumps off unless you need them.
