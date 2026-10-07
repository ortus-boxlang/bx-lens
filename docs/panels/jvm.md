---
title: JVM and Runtime
order: 9
description: Runtime, memory and global activity.
icon: lucide:cpu
---

# JVM and Runtime

The JVM panel shows runtime and memory information. The health strip uses the memory figure.

Global activity such as scheduled tasks, executors, watchers and module loads is not tied to one request. Lens keeps a rolling view of it in the service, fed by the scheduler, watcher and module events. See [Core events](../reference/events.md#global-events).

## Thread dump

Thread dumps are off. Enable `collectors.jvm.threadDump` to allow them. Heap dumps need the separate `allowHeapDump` opt-in. Neither writes outside the temp directory. See [Security](../security.md#dumps).
