---
title: Extending Lens
order: 2
description: Add panels, spans and issues from a module or from app code.
icon: lucide:puzzle
---

# Extending Lens

Every BoxLang module has its own class loader, so a module cannot implement Java interfaces from inside bx-lens. Lens therefore uses a data-first contract. Tier 1 needs no shared classes and works from BoxLang or Java modules.

::: cards
::: card title="Tier 1: data contributions" icon="lucide:check"
Available in v1. Register panels and fill them with data through interception points.
:::
::: card title="Tier 2: Java collectors" icon="lucide:clock"
Planned for v1.x. Under design.
:::
::: card title="Tier 3: custom UI" icon="lucide:clock"
Planned for later. Under design.
:::
:::

## Interception points

Lens registers these points. Your code only listens.

| Point | When | Payload |
|---|---|---|
| `onLensRegister` | At Lens activation, and again on `postModuleLoad` | `registry` with `registry.panel( id, label, icon, order, renderer )` |
| `onLensRequestStart` | Per request, after the request object exists | `context`, `requestId` |
| `onLensCollect` | Per request, before render | `context`, `requestId`, `panel( id )` handle to add data |
| `onLensRequestFinish` | Per request, after the request is stored | `requestId`, `summary` |

## From a module

Declare the panel once, then fill it for each request.

Register an interceptor in your module:

```javascript title="ModuleConfig.bx"
function configure() {
	interceptors = [
		{ class : "#moduleMapping#.interceptors.MyLens", name : "MyLens@mymodule" }
	];
}
```

Then write the interceptor:

```javascript title="interceptors/MyLens.bx"
class {

	@InterceptionPoint
	function onLensRegister( event ) {
		event.registry.panel( id : "orm", label : "ORM", icon : "database", order : 40, renderer : "table" );
	}

	@InterceptionPoint
	function onLensCollect( event ) {
		var rows = [ [ "User", "load", 4 ], [ "Order", "flush", 31 ] ];
		var slow = rows.some( ( r ) => r[ 3 ] > 25 );
		event.panel( "orm" )
			.columns( [ "Entity", "Action", "ms" ] )
			.rows( rows )
			.badge( rows.len(), slow ? "warn" : "none" );
	}

}
```

## From app code

You can build a panel without a module. Use `lensPanel` anywhere in a request.

```javascript
lensPanel( "checkout", "Checkout" )
	.columns( [ "Step", "ms" ] )
	.rows( [ [ "validate", 4 ], [ "charge", 220 ] ] )
	.badge( 2, "none" );
```

## Renderers

Renderers ship inside Lens, so your panel needs no JavaScript. Set one with the `renderer` argument or with the matching builder method.

| Renderer | Use it for |
|---|---|
| `table` | Rows and columns. |
| `kv` | Key and value pairs. |
| `tree` | Nested data. |
| `spans` | Timed items. These feed the Timeline. |
| `messages` | Log style lines. |
| `json` | Raw structured data. |
| `text` | Plain text. |

### Spans

A `spans` panel supplies a type, a label, a start and a duration for each item. Lens draws them in the [Timeline](../panels/timeline.md) with their own color and filter chip.

### Issues

A panel can add issues. They show on the [Issues](../panels/issues.md) tab and drive the strip color.

```javascript
event.panel( "orm" ).issue(
	"warn",
	"Slow flush",
	"Flushing Order took 31 ms",
	"/app/models/Order.bx",
	88
);
```

The arguments are `severity, title, detail, file, line`.

## Safety

Module data goes through the same redaction, size caps and JSON escaping as built-in collectors. The UI always escapes text. Tier 1 cannot ship HTML or script.

## Later tiers

These are planned and not available yet.

- **Tier 2, Java collectors (v1.x).** For hot paths where a module wants typed, fast collection. The spec weighs a small `bx-lens-api` jar or a static facade. It needs a class loader spike first.
- **Tier 3, custom UI (later).** A module ships a small ES module served from `/~bxlens/ext/{module}/` and declares `renderer: "custom"`. It will stay off behind `ui.allowCustomPanels`, because it runs script in the page.

Candidate first-party contributors named in the spec: bx-orm, bx-redis, bx-mail, bx-ai, bx-jdbc drivers, Quick and qb, and ColdBox and cbwire.
