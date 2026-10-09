---
title: HTTP
order: 5
description: Outgoing HTTP calls made during the request.
icon: lucide:globe
---

# HTTP

The HTTP panel lists outgoing calls made with `bx:http` during the request. Each row shows the status, size and time.

![The HTTP panel with a successful call and a 404](../assets/screenshots/http.png)

An outgoing 4xx response warns. A 5xx response or a transport failure is critical. Calls also appear as spans on the [Timeline](timeline.md).

`collectors.http.max` caps the entries (default 100).
