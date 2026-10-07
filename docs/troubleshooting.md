---
title: Troubleshooting
order: 6
description: Fixes for a missing bar and other common problems.
icon: lucide:wrench
---

# Troubleshooting

## The bar does not appear

Check these in order.

1. **Is Lens enabled?** `modules.bxLens.settings.enabled` must be `true`. Restart after you change it.
2. **Is the caller allowed?** By default only loopback and private networks pass. See `access` in [Configuration](configuration.md#access). Remote and Docker hosts often need an entry in `access.allowedIPs`.
3. **Is the response HTML?** The `Content-Type` must start with an entry in `contentTypes`. JSON, SSE, files and redirects get no bar.
4. **Is the path excluded?** Check `excludePaths`.
5. **Is the response committed?** Lens cannot inject after the response is sent, for example after a `flush`.
6. **Is `inject` false?** Then you must call `lensRender()`.

## A JSON or ajax request shows nothing

That is expected. Open the [History](panels/history.md) tab on an HTML page and find the request. Lens returns its id in the `X-BxLens-Id` header.

## A panel is missing

`functions`, `cache`, `logs` and `session` are off by default. Enable them under `collectors`. A collector that fails logs the error to the `bxLens` logger and is skipped for that request, so check the log.

## The Cache panel shows no hit or miss data

Core has no cache read event. See [Core gaps](reference/events.md#core-gaps).

## Open in editor does not work

Set `editor.linkPattern` for your editor. When the app runs in a container or on another host, map paths with `editor.remoteBase` and `editor.localBase`.

## Values show as redacted

Keys listed in `redact.keys` are masked on the server. Remove a key from the list only if the value is not sensitive.

## A setting has no effect

Lens logs a warning for unknown keys. Check the log for typos. Settings live under `modules.bxLens.settings`.

## Report a problem

Open an issue at [github.com/ortus-boxlang/bx-lens/issues](https://github.com/ortus-boxlang/bx-lens/issues). Include your BoxLang version, runtime (MiniServer, servlet or CommandBox) and relevant settings.
