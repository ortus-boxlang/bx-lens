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
| [`lensPanel`](#lenspanel) | `( id, label )` | a panel builder |

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

Returns the bar markup as a string. Use it with `inject` set to `false` when you want to place the bar yourself.

```javascript
writeOutput( lensRender() );
```

## lensDump

Pushes a value to the Messages panel as a collapsible tree. The optional label names it.

```javascript
lensDump( session.cart, "cart" );
```

## lensPanel

Creates or fetches a custom panel from app code. No module needed. The panel uses one of the built-in renderers and the builder methods shown in [Extending Lens](extending.md#from-app-code).

```javascript
lensPanel( "jobs", "Jobs" )
	.columns( [ "Job", "ms" ] )
	.rows( [ [ "email", 12 ], [ "report", 340 ] ] );
```

## The bx:lens component

Templates can use the component form. The `action` attribute accepts `message`, `start`, `stop` or `dump`.

```html
<bx:lens action="message" message="Header rendered">
```

!!! note
    The spec defines the `action` values. The other attributes are assumed to mirror the BIF arguments.
