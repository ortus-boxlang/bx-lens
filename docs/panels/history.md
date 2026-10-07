---
title: History
order: 12
description: The last 50 requests, including JSON and SSE.
icon: lucide:history
---

# History

History lists recent requests, newest first. The current request is marked.

![The History panel listing HTML, JSON and SSE requests](../assets/screenshots/history.png)

## How it works

- Lens keeps finished requests in an in-memory ring buffer. The default size is 50 (`history.maxRequests`).
- When the buffer is full, Lens recycles the oldest request.
- History resets on a restart or a module reload. Lens never writes it to disk.
- Each row shows a type pill, status, time, SQL count and issue count.
- Rows show summaries only. You cannot load the full detail of an earlier request.

## Non-HTML requests

JSON, SSE, file and redirect responses get no bar. With `history.trackNonHtml` on (the default), Lens still records them and returns the request id in the `X-BxLens-Id` header. They then appear in History. This helps when you debug an API or an ajax call.

```bash
curl -i http://localhost:8085/api/orders.json.bxm
# X-BxLens-Id: 7f3c...
```

## Uncaught errors

A request that ends in an uncaught exception gets core's error page without a bar. Lens still records it in History with its exception and issue.

Comparing two requests is planned. See the [Roadmap](../project/roadmap.md).
