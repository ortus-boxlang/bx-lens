---
title: Request and Scopes
order: 8
description: Request details and scope contents.
icon: lucide:braces
---

# Request and Scopes

## Request

The Request panel shows the method, URL, status, headers and params of the current request. Copy it as JSON or as a cURL command. Lens masks header and param values whose key matches `redact.keys`. The `Cookie` and `Authorization` headers are masked by default.

![The Request panel after a form post](../assets/screenshots/request.png)

## Scopes

The Scopes panel shows scope contents. `url` and `form` are on by default. `cookie`, `session`, `request`, `application` and `variables` are off. Turn them on under `collectors.scopes`.

![The Scopes panel with a redacted password field](../assets/screenshots/scopes.png)

`limits.maxDepth` (4), `limits.maxItems` (100) and `limits.maxString` (2000) cap what Lens sends.

!!! warning
    Scope dumps can hold secrets. Redaction covers keys that contain an entry from `redact.keys`. Add your own keys before you enable a scope. See [Security](../security.md).
