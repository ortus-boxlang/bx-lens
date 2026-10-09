---
title: Performance
order: 5
description: What Lens costs a request, measured before and after the performance work.
icon: lucide:gauge
---

# Performance

Requests per second on the demo harness (MiniServer, one JVM with 1 GB heap, 8 concurrent clients, 12 seconds per page after a warm up, derby in memory). The machine is shared, so read the figures as relative: a run to run difference of 10% is noise. The scripts are in the repository history of this page (`run.sh`, `load.cjs`).

"Before" is the build before the performance work started (owner measurement where one exists, else the build with the bar inlined). "After" is the current build.

| Mode | /index.bxm before | /index.bxm after | /orders.bxm after | /n-plus-one.bxm after | /functions.bxm after |
|---|---|---|---|---|---|
| Lens installed, bar and console off | 2110 to 2400 | 2773 | 1960 | 1737 | 2193 |
| Console on, `collect.level` light | 1549 | 2194 | 1819 | 1812 | 1935 |
| Console on, `collect.level` full | 1501 to 1539 | 2068 | 1801 | 1344 | 1900 |
| Bar shown, light | not measured | 1654 | 1408 | 1266 | 1564 |
| Bar shown, full, console on | 217 | 1617 | 1373 | 1007 | 1617 |

What changed the numbers:

- **The bar page** was the big one. Every page used to carry about 300 KB of styles, script, Alpine and icons, copied into the response. It now carries about 1 KB and the files are cached by the browser for a year. That is the move from 217 to about 1600 requests a second.
- The snapshot JSON is built when somebody looks at it (the bar renders it only for an allowed caller, the console builds it when it opens a request), not for every request. Issue analysis runs only for the console, when it opens a request.
- Statistics, the history entry and the audit log run on one background thread with a bounded queue.
- A request walks the collectors without a lock, caller addresses and proxy settings are resolved once, event data for Lens's own interception points is built only when something listens, ids are a counter and the time instead of a random UUID, and no regular expression runs on a request.

At `light` a profile (JFR) shows Lens in about 5% of the CPU samples. Most of what is left in the full modes is the lookup of the calling template and line for every query, which `collectors.queries.captureCaller` turns off.
