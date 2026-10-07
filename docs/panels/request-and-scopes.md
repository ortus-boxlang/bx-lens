---
title: Request and Scopes
order: 8
description: Request details and scope contents.
icon: lucide:braces
---

# Request and Scopes

## Request

The Request panel shows the method, URL, status, headers and params of the current request. You can copy it as JSON or as a cURL command. Lens redacts header and param values that match `redact.keys`.

## Scopes

The Scopes panel shows scope contents. `form` and `url` are on by default. `cookie`, `session`, `request` and `application` are off. Turn them on under `collectors.scopes`.

| Setting | Default | Meaning |
|---|---|---|
| `maxDepth` | `4` | How deep Lens walks nested values. |
| `maxBytes` | `65536` | Size cap for the scope payload. |

!!! warning
    Scope dumps can hold secrets. Redaction covers the keys in `redact.keys`. Add your own keys before you enable a scope. See [Security](../security.md).
