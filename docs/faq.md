---
title: FAQ
order: 7
description: Short answers to common questions.
icon: lucide:circle-help
---

# FAQ

::: expandable "Is Lens safe for production?"
No. It ships disabled and you should never enable it in production. See [Security](security.md).
:::

::: expandable "Does Lens store data?"
Only in memory. History keeps the last 50 requests and clears on restart. There is no disk or database storage.
:::

::: expandable "Does it work with APIs and JSON responses?"
Those responses get no bar, but Lens collects and stores them by default. Find them in [History](panels/history.md).
:::

::: expandable "Does it work with ColdBox, Quick or CBWIRE?"
Lens works at the request level, so it sees what core announces. First-party panels for ColdBox, cbwire and Quick are candidates in the spec, not shipped features.
:::

::: expandable "How do I add my own panel?"
Use `lensPanel` in app code or the Tier 1 interception points in a module. See [Extending Lens](guides/extending.md).
:::

::: expandable "Can I turn Lens off for one request?"
Yes. Call `lensDisable()`, or send `X-BxLens: off` if `access` allows you.
:::

::: expandable "How is Lens different from BX Insights?"
Lens is an open source, request-level debug bar. BX Insights is a separate commercial observability product. See the [Roadmap](project/roadmap.md#relation-to-bx-insights).
:::

::: expandable "Why does the Cache panel lack hit and miss events?"
Core does not announce them yet. See [Core gaps](reference/events.md#core-gaps).
:::

::: expandable "Does it need Node or a build step in my app?"
No. Lens serves its own assets from `/~bxlens/`.
:::
