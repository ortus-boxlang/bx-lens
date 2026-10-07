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
5. **Did the request end in an uncaught error or abort?** Core renders its own error page and Lens cannot inject into it. Open the next HTML page and check [History](panels/history.md).
6. **Is the response committed?** Lens cannot inject after the response is sent, for example after a `flush`.
7. **Is `inject` false?** Then you must call `lensRender()`.

## A JSON or ajax request shows nothing

That is expected. Open the [History](panels/history.md) tab on an HTML page and find the request. Lens returns its id in the `X-BxLens-Id` header.

## A panel is missing

`functions` and `logs` are off by default. Enable them under `collectors`. A collector that fails logs the error to the `bxLens` logger and is skipped for that request, so check the log.

## Cache numbers for the request look off

Core announces no cache read events, so Lens compares cache statistics from the start and end of the request. Parallel requests can add to the numbers. See [Cache](panels/cache.md).

## Open in editor does not work

Set `editor.linkPattern` for your editor. When the app runs in a container or on another host, map paths with `editor.remoteBase` and `editor.localBase`.

## Values show as redacted

Keys listed in `redact.keys` are masked on the server. Remove a key from the list only if the value is not sensitive.

## A setting has no effect

Settings live under `modules.bxLens.settings`. Restart the runtime after you change them.

## The panel takes extra page weight

Lens inlines its assets, about 110 KB per HTML response. Exclude paths with `excludePaths` or call `lensDisable()` for heavy pages.

## Report a problem

Open an issue at [github.com/ortus-boxlang/bx-lens/issues](https://github.com/ortus-boxlang/bx-lens/issues). Include your BoxLang version (1.19 or newer), runtime (MiniServer, servlet or CommandBox) and relevant settings.
