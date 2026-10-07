---
title: Messages and Timers
order: 7
description: Log your own messages, dumps and timings.
icon: lucide:message-square
---

# Messages and Timers

## Messages

Messages shows output from [`lensMessage`](../guides/bifs.md#lensmessage) and [`lensDump`](../guides/bifs.md#lensdump). Structs and arrays serialize into a collapsible tree. Lens also routes `bx:dump` output here instead of the page body.

```javascript
lensMessage( "Cart loaded", "info" );
lensMessage( { items : 3, total : 42.5 }, "debug" );
lensDump( cart, "cart" );
```

`collectors.messages.max` caps the list (default 200).

## Timers

Timers shows spans from [`lensStart`](../guides/bifs.md#lensstart), [`lensStop`](../guides/bifs.md#lensstop), [`lensMeasure`](../guides/bifs.md#lensmeasure) and [`lensAddMeasure`](../guides/bifs.md#lensaddmeasure).

```javascript
lensStart( "report" );
buildReport();
lensStop( "report" );
```

`collectors.timers.max` caps the list (default 200).
