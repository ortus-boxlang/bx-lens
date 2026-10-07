---
title: BIF Reference
order: 1
description: Built-in functions for talking to Lens from your code.
icon: lucide:code
---

# BIF Reference

These functions work whenever the module is loaded. When Lens is disabled for the request, or the caller is not allowed, they do nothing.

| Function | Signature | Returns |
|---|---|---|
| [`lensMessage`](#lensmessage) | `( message, label="info" )` | nothing |
| [`lensStart`](#lensstart) | `( label )` | id |
| [`lensStop`](#lensstop) | `( labelOrId )` | nothing |
| [`lensMeasure`](#lensmeasure) | `( label, callable )` | the callable's result |
| [`lensAddMeasure`](#lensaddmeasure) | `( label, startMs, endMs )` | nothing |
| [`lensException`](#lensexception) | `( exception )` | nothing |
| [`lensEnable`](#lensenable-and-lensdisable) / [`lensDisable`](#lensenable-and-lensdisable) | `()` | nothing |
| [`lensIsEnabled`](#lensisenabled) | `()` | boolean |
| [`lensRender`](#lensrender) | `()` | string |
| [`lensDump`](#lensdump) | `( value, label="" )` | nothing |
| [`lensPanel`](#lenspanel) | `( id, label, renderer )` | a panel builder |

## lensMessage

Adds a message to the [Messages](../panels/messages-and-timers.md#messages) panel. `label` can be `info`, `warn`, `error`, `debug` or any string. Structs and arrays are serialized.

```javascript
lensMessage( "Cache warmed" );
lensMessage( "Retrying payment", "warn" );
lensMessage( { orderId : 42, items : [ 1, 2, 3 ] }, "debug" );
```

## lensStart

Starts a timer and returns its id.

```javascript
id = lensStart( "import" );
```

## lensStop

Ends a timer. Pass the label or the id. Lens ignores labels that match no running timer.

```javascript
lensStart( "import" );
runImport();
lensStop( "import" );
```

## lensMeasure

Times a closure and returns its result.

```javascript
orders = lensMeasure( "load orders", () => {
	return queryExecute( "SELECT * FROM orders" );
} );
```

## lensAddMeasure

Adds a span you measured yourself. Pass start and end in milliseconds.

```javascript
start = getTickCount();
doWork();
lensAddMeasure( "doWork", start, getTickCount() );
```

## lensException

Records an exception you caught. See [Exceptions](../panels/exceptions.md).

```javascript
try {
	callPaymentApi();
} catch ( any e ) {
	lensException( e );
}
```

## lensEnable and lensDisable

Switch Lens on or off for the current request. The `access` rules still apply, so a blocked caller stays blocked.

```javascript
lensDisable(); // keep a noisy endpoint out of Lens
```

## lensIsEnabled

Returns true when Lens is active for the current request.

```javascript
if ( lensIsEnabled() ) {
	lensMessage( "Lens is on" );
}
```

## lensRender

Use it with `inject` set to `false` when you want to place the bar yourself. It returns a placeholder that Lens replaces with the bar when the request ends.

```javascript
writeOutput( lensRender() );
```

## lensDump

Pushes a value to the Messages panel as a collapsible tree. The optional label names it.

```javascript
lensDump( session.cart, "cart" );
```

## lensPanel

Creates a custom panel from app code. No module is needed. `renderer` is optional and defaults to `table`. The builder methods set the renderer for you. See [Extending Lens](extending.md#from-app-code).

```javascript
lensPanel( "jobs", "Jobs", "table" )
	.columns( [ "Job", "ms" ] )
	.rows( [ [ "email", 12 ], [ "report", 340 ] ] )
	.badge( 2, "none" );
```

Builder methods: `columns`, `rows`, `kv`, `tree`, `spans`, `messages`, `json`, `text`, `label`, `icon`, `order`, `badge( count, severity )` and `issue( severity, title, detail, file, line )`.

## Not available

These are not implemented in v1: `lensDumpHeap`, `lensThreadDump` and the `bx:lens` component. Use the BIFs above in templates inside `<bx:script>` blocks.
