---
title: Runtime
order: 9
description: JVM, memory and request statistics.
icon: lucide:cpu
---

# Runtime

The Runtime panel (collector `jvm`) shows the BoxLang and Java versions, the OS, CPU cores and uptime. It also shows heap use, the heap change during the request, non-heap memory, garbage collections and threads, plus request statistics: total requests tracked, average and slowest time, and active sessions. The health strip uses the heap figure.

## Request cost

!!! note "BoxLang+"
    Request cost needs BoxLang+ or a trial. On Free the Runtime panel shows a BoxLang+ note, Lens does not measure the request and the `cpu` and `alloc` chips are hidden. The rest of the Runtime panel is free.

The panel also lists what the request cost its thread:

| Value | Meaning |
|---|---|
| CPU time | CPU time used by the request thread, from the JDK thread bean. |
| Allocated | Bytes the request thread allocated. |

The strip shows the same two numbers as the `cpu` and `alloc` chips. Work that runs on other threads (for example `asyncRun`) is not included. If the JVM cannot measure a value, the chip is hidden and the panel says "not measurable".

![The Runtime panel](../assets/screenshots/runtime.png)

The bar offers no heap dumps. For JVM-wide numbers and a thread dump (free), use the console [System](../console/system-and-threads.md) and [Threads](../console/system-and-threads.md#threads) pages.
