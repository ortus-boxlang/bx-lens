---
title: System and Threads
order: 4
description: JVM and host numbers, plus a thread viewer and thread dump.
icon: lucide:cpu
---

# System and Threads

## System

The System page shows JVM and host numbers from the JDK management beans. It updates live and keeps a short chart of CPU, heap and thread count while you watch.

![The System page](../assets/screenshots/console-system.png)

| Block | Content |
|---|---|
| CPU | Process CPU and system CPU in percent, core count, load average, OS and architecture. |
| Heap | Used and maximum heap, plus non-heap. |
| Threads | Live, peak and daemon threads, and deadlocked threads. |
| Memory pools | Each pool with used and maximum. |
| Garbage collection | Runs and total time per collector since start. |
| Host memory | RAM and swap use, when the JVM can report them. |
| Disks | Total and used space per file system root. |
| Runtime | VM, vendor, Java version, PID, uptime, loaded classes and the JVM flags. Flags that look sensitive are masked. |
| System properties | A selected, fixed list (OS, time zone, encoding, Java home and similar). It is not a full dump. |

## Threads

The Threads page lists every JVM thread with its state, CPU time and blocked time. It refreshes every 5 seconds while open, not through the live stream, because taking a thread dump pauses the JVM briefly.

![The Threads page](../assets/screenshots/console-threads.png)

- Filter by state (runnable, waiting, timed waiting, blocked), by pool, or by name or frame.
- Threads running BoxLang templates or functions are marked **BoxLang**, and the thread that serves your own call is marked **this request**.
- Pick a thread to see its stack. BoxLang frames are highlighted, and a waiting thread shows the lock and its owner. Copy the stack with one click.
- A deadlock shows a red banner with the number of threads involved.
- **Copy dump** puts a jstack-style dump on the clipboard. **Download dump** saves it as `lens-thread-dump-{timestamp}.txt`.

Turn either page off with `collectors.system.enabled` or `collectors.threads.enabled` (or `tabs.hide`).
