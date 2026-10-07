---
title: Queries
order: 3
description: SQL timing, parameters, duplicates and N+1 detection.
icon: lucide:database
---

# Queries

The Queries panel lists each SQL statement the request ran, with its datasource, duration, row count and parameters.

![The Queries panel with a flagged N+1 group](../assets/screenshots/queries.png)

## Detection

::: cards
::: card title="Slow queries" icon="lucide:timer"
A query slower than `thresholds.slowQueryMs` (default 25 ms) warns. At 4 times the threshold it is critical.
:::
::: card title="N+1" icon="lucide:repeat"
The same SQL with placeholders run at least `thresholds.nPlusOneMin` times (default 3) is flagged as N+1.
:::
::: card title="Caller" icon="lucide:map-pin"
With `captureCaller`, each query records the template and line that ran it.
:::
:::

Flagged queries appear on the [Issues](issues.md) tab, and each issue links back to its row.

## Parameters

`collectors.queries.includeParams` controls whether Lens shows bound parameters. Values pass through [redaction](../security.md#redaction). The Copy SQL button copies the statement with its params filled in.

## Example

This loop triggers the N+1 flag:

```javascript
users = queryExecute( "SELECT id FROM users" );
for ( user in users ) {
	queryExecute(
		"SELECT * FROM orders WHERE user_id = :id",
		{ id : user.id }
	);
}
```

Fix it with one joined query or a single `WHERE user_id IN (...)`.
