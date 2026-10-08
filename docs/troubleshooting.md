---
title: Troubleshooting
order: 6
description: Fixes for a missing bar and other common problems.
icon: lucide:wrench
---

# Troubleshooting

## The bar does not appear

Check these in order.

1. **Is the bar enabled?** `modules.bxLens.settings.bar.enabled` must be `true`. Restart after you change it. (The old top-level `enabled` is no longer read, see [Configuration](configuration.md#moving-from-older-settings).)
2. **Is the caller allowed?** By default only loopback passes (`bar.access` is `"local"`). Add `"private"`, IPs or CIDR ranges. Remote and Docker hosts often need an entry. If you set `"all"` without `bar.allowAllIPs: true`, Lens falls back to loopback and logs an error. `access.allowedHosts` and `access.requireHeader` apply too. See [Configuration](configuration.md#bar).
3. **Is the response HTML?** The `Content-Type` must start with an entry in `contentTypes`. JSON, SSE, files and redirects get no bar.
4. **Is the path excluded?** Check `excludePaths`.
5. **Did the request end in an uncaught error or abort?** Core renders its own error page and Lens cannot inject into it. Open the next HTML page and check [History](panels/history.md).
6. **Is the response committed?** Lens cannot inject after the response is sent, for example after a `flush`.
7. **Is `inject` false?** Then you must call `lensRender()`.

## The console shows a 404

- Use `/~bxlens/index.bxm`. A bare `/~bxlens/` is not served.
- A caller outside `console.access`, `access.allowedHosts` or `access.requireHeader` gets a plain 404 on purpose.
- `console.enabled` must be `true`.

## The console shows a setup page (503)

`console.password` is empty or could not be decrypted. Check the log for `console.password is empty or cannot be decrypted`. A `bxsecret:` value only decrypts with the seed it was made with, so create it with `boxlang generatesecret` on a runtime that uses the same seed.

## I cannot sign in

Wrong passwords are counted per address. After `console.maxLoginAttempts` the address is locked for `console.lockoutMinutes` and the page says how long. A restart signs everyone out. Behind a proxy, all callers may share one address, so one person's mistakes can lock out the rest.

## Live data does not update

The console uses one Server-Sent Events stream and polls every 3 seconds if it cannot. A proxy that buffers responses can break the stream. Turn buffering off for `/~bxlens/index.bxm/stream` (the response sends `X-Accel-Buffering: no`). `console.maxStreams` caps open streams.

## Run now or Pause does nothing

`console.actions` may be `false`. The buttons are greyed out and the server refuses the call.

## A JSON or ajax request shows nothing

That is expected. Open the [History](panels/history.md) tab on an HTML page and find the request. Lens returns its id in the `X-BxLens-Id` header.

## A panel is missing

`functions` and `logs` are off by default. Enable them under `collectors`. At `collect.level: "light"` the `functions`, `logs` and `scopes` collectors are skipped. A tab listed in `tabs.hide`, or a collector that is disabled, hides its tab. A collector that fails logs the error to the `bxLens` logger and is skipped for that request, so check the log.

## Cache numbers for the request look off

Core announces no cache read events, so Lens compares cache statistics from the start and end of the request. Parallel requests can add to the numbers. See [Cache](panels/cache.md).

## Open in editor does not work

Set `editor.linkPattern` for your editor. When the app runs in a container or on another host, map paths with `editor.remoteBase` and `editor.localBase`.

## Values show as redacted

Keys listed in `redact.keys` are masked on the server. Remove a key from the list only if the value is not sensitive.

## A setting has no effect

Settings live under `modules.bxLens.settings`. Restart the runtime after you change them. The Settings page in the console shows the values Lens is really using.

## The panel takes extra page weight

Lens inlines its assets, about 110 KB per HTML response. Exclude paths with `excludePaths` or call `lensDisable()` for heavy pages.

## Report a problem

File an issue in the [BLMODULES Jira project](https://ortussolutions.atlassian.net/browse/BLMODULES) or contact [Ortus Solutions support](https://www.ortussolutions.com/services/support). Include your BoxLang version (1.19 or newer), runtime (MiniServer, servlet or CommandBox) and relevant settings (never the console password).
