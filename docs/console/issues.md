---
title: Issues
order: 8
description: Everything suspicious in one ranked list.
icon: lucide:circle-alert
---

# Issues

The console request detail lists everything suspicious about a request, ranked by severity. The bar does not: it only shows what happened. Issues are found when the console opens the request (once, then kept), so a bar only installation never runs the analysis.

An unfinished request (see [Core events](../reference/events.md#spans-that-never-close)) gets the critical issue **Request never finished**.

![The Issues tab with ranked findings](../assets/screenshots/issues.png)

## Sources

| Source | Trigger |
|---|---|
| N+1 | The same SQL with placeholders run `thresholds.nPlusOneMin` times (default 3) or more. |
| Slow query | Warn above `thresholds.slowQueryMs`, critical at 4 times that. |
| Slow template or function | Self time above `thresholds.slowTemplateMs`. The first run of a template includes compilation. |
| Slow request | Longer than `thresholds.slowRequestMs`. Names the template and line when a [sample](#slow-request-sample) was taken. |
| Security notes | Missing security headers or cookie flags on an HTML response. See [below](#security-notes). |
| Request status | The request itself returns 4xx (warn) or 5xx (critical). |
| Outgoing HTTP | A 4xx response warns. A 5xx response or transport failure is critical. |
| Exception | Critical. |
| Panel issue | Added by a custom panel with `issue()`. See [Extending Lens](../guides/extending.md#issues). |

Issues drive the color of the health strip and the issues chip: amber for warnings, red for critical findings. Notes (severity `info`) are listed here as **Note**, are not counted, and do not color the strip.

## Slow request sample

!!! note "BoxLang+"
    The slow request sample needs BoxLang+ or a trial. On Free no sample is taken, so the Slow request issue has no location. See [Licensing](../licensing.md#free-and-boxlang).

When a request runs longer than `thresholds.slowRequestMs`, a watchdog thread takes one stack sample of the request thread. It checks every 200 ms, so the sample comes shortly after the limit. The **Slow request** issue then says where the request was, for example `At 3412 ms it was in stall.bxm:5`, and links to that file and line. The first BoxLang frame in the stack is used. The sample is taken once per request and shows in the Request detail in the console as "Where it was at".

Turn it off with `checks.slowSample: false`. It does nothing when `thresholds.slowRequestMs` is 0.

## Security notes

On HTML responses with a status from 200 to 399, Lens checks (setting `checks.securityHeaders`, on by default):

| Note | Raised when |
|---|---|
| Security headers missing | The response lacks `Content-Security-Policy`, `X-Frame-Options` (not needed when the CSP has `frame-ancestors`), `X-Content-Type-Options`, or `Strict-Transport-Security` (HTTPS requests only). |
| Cookie flags missing | A cookie the response sets has no `HttpOnly`, no `Secure` (HTTPS only), or no `SameSite`. |

The note names the missing headers or the cookies. Add them in your web server or `Application.bx`. JSON and other non-HTML responses are not checked.
