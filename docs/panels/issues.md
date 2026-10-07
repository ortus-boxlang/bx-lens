---
title: Issues
order: 13
description: Everything suspicious in one ranked list.
icon: lucide:circle-alert
---

# Issues

The Issues tab lists everything suspicious about the request, ranked by severity. Each issue links to its row in the panel that raised it.

![The Issues tab with ranked findings](../assets/screenshots/issues.png)

## Sources

| Source | Trigger |
|---|---|
| N+1 | The same SQL with placeholders run `thresholds.nPlusOneMin` times (default 3) or more. |
| Slow query | Warn above `thresholds.slowQueryMs`, critical at 4 times that. |
| Slow template or function | Self time above `thresholds.slowTemplateMs`. The first run of a template includes compilation. |
| Slow request | Longer than `thresholds.slowRequestMs`. |
| Request status | The request itself returns 4xx (warn) or 5xx (critical). |
| Outgoing HTTP | A 4xx response warns. A 5xx response or transport failure is critical. |
| Exception | Critical. |
| Panel issue | Added by a custom panel with `issue()`. See [Extending Lens](../guides/extending.md#issues). |

Issues drive the color of the health strip and the issues chip: amber for warnings, red for critical findings.
