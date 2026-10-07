---
title: Cache
order: 10
description: BoxCache statistics and what the request did to them.
icon: lucide:zap
---

# Cache

The Cache panel lists every registered BoxCache cache. For each cache it shows the provider, objects, hits, misses, hit rate, evictions, reaps, expired entries, garbage collections and the configuration.

![The Cache panel with one card per cache](../assets/screenshots/cache.png)

It also shows what this request did: hits, misses, hit rate and evictions.

!!! note "How the request numbers work"
    Core announces no cache read events. Lens reads each cache's statistics at the start and at the end of the request and shows the difference. Other requests that run at the same time can affect the numbers.

The panel is on by default. Turn it off with `collectors.cache.enabled`.
