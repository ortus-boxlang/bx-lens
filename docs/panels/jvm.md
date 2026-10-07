---
title: Runtime
order: 9
description: JVM, memory and request statistics.
icon: lucide:cpu
---

# Runtime

The Runtime panel (collector `jvm`) shows the BoxLang and Java versions, the OS, CPU cores and uptime. It also shows heap use, the heap change during the request, non-heap memory, garbage collections and threads, plus request statistics: total requests tracked, average and slowest time, and active sessions. The health strip uses the heap figure.

![The Runtime panel](../assets/screenshots/runtime.png)

Lens does not offer heap dumps or thread dumps in v1.
