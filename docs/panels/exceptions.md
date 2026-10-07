---
title: Exceptions
order: 6
description: Thrown and caught exceptions.
icon: lucide:triangle-alert
---

# Exceptions

The Exceptions panel records errors thrown by the request and exceptions your code catches and reports.

Lens captures these through the `onError` event and function exceptions. To record an exception you caught yourself, call [`lensException`](../guides/bifs.md#lensexception):

```javascript
try {
	riskyCall();
} catch ( any e ) {
	lensException( e );
	// handle the error
}
```

A caught exception turns the strip amber or red and adds an issue. By default Lens opens this tab for you (`ui.autoOpenOnException`).

`collectors.exceptions.max` caps the list (default 50). Lens also injects the bar on error pages, so you can inspect a failed request.
