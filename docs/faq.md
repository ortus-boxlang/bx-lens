---
title: FAQ
order: 7
description: Short answers to common questions.
icon: lucide:circle-help
---

# FAQ

::: expandable "Is Lens safe for production?"
The bar is a development tool, keep it off. The console can run on a live server if you set a `bxsecret:` password, a tight `console.access` list, HTTPS and `collect.level: "light"`. See [Running Lens in production](guides/production.md).
:::

::: expandable "What is the difference between the bar and the console?"
The bar is a strip on your HTML pages that shows one request. The console is a separate page at `/~bxlens/index.bxm` for one server: requests, executors, tasks, system and threads. They are switched on independently with `bar.enabled` and `console.enabled`. See [Console](console/index.md).
:::

::: expandable "Why does /~bxlens/ give a 404?"
A bare `/~bxlens/` is not served by MiniServer. Use `/~bxlens/index.bxm`. A caller that is not allowed by `console.access` also gets a plain 404 on purpose.
:::

::: expandable "How do I make the console password?"
Run `boxlang generatesecret "your password"` and put the `bxsecret:` value in `console.password`. See [Console](console/index.md#make-the-password).
:::

::: expandable "Is BX Lens open source? Does it need a license?"
BX Lens is a product of Ortus Solutions and license terms apply. Which features need BoxLang+ is not decided yet, so every feature works in every license state today. See [Licensing](licensing.md).
:::

::: expandable "Does the console call out to the internet?"
No. Alpine.js and the Phosphor icons are bundled and fonts are system fonts. A strict Content-Security-Policy blocks any other request, so it works on an air gapped network.
:::

::: expandable "Does Lens store data?"
Only in memory. History keeps the last 50 requests (`history.maxRequests`) and clears on restart or module reload. The one file Lens writes is the bar layout you save in the designer, `config/bxlens-layout.json` in the BoxLang home. There is no database.
:::

::: expandable "Does it work with APIs and JSON responses?"
Those responses get no bar, but Lens collects and stores them by default. Find them in [History](panels/history.md).
:::

::: expandable "Does it work with ColdBox, Quick or CBWIRE?"
Lens works at the request level, so it sees what core announces. Panels for ColdBox, cbwire and Quick are ideas, not shipped features.
:::

::: expandable "How do I add my own panel?"
Use `lensPanel` in app code or the interception points in a module. See [Extending Lens](guides/extending.md).
:::

::: expandable "Can I turn Lens off for one request?"
Yes. Call `lensDisable()` early in the request.
:::

::: expandable "Why do I see Notes about security headers?"
Lens checks HTML responses for a missing Content-Security-Policy, X-Frame-Options, X-Content-Type-Options, HSTS on HTTPS, and cookie flags. They are Notes: they do not count as issues and do not color the strip. Turn them off with `checks.securityHeaders`. See [Issues](panels/issues.md#security-notes).
:::

::: expandable "How is Lens different from BX Insights?"
Lens covers one server and one request at a time. BX Insights is the separate observability product for clusters, history over time and alerting. See the [Roadmap](project/roadmap.md#relation-to-bx-insights).
:::

::: expandable "How does the Cache panel count hits and misses for a request?"
Core announces no cache read events. Lens subtracts the cache statistics at request start from those at request end. See [Cache](panels/cache.md).
:::

::: expandable "Why is there no bar on my error page?"
Core renders its own page for uncaught exceptions and skips the event Lens uses. The request is still in [History](panels/history.md).
:::

::: expandable "Does it need Node or a build step in my app?"
No. The bar inlines its own assets into each HTML response, about 110 KB. The console serves its own files.
:::
