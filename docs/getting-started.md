---
title: Getting Started
order: 2
description: Install BX Lens, enable it and see the bar on your first page.
icon: lucide:rocket
---

# Getting Started

## Requirements

- BoxLang 1.19 or newer. The module is built against 1.19.0-snapshot.
- Java 21.
- A BoxLang web runtime: MiniServer, a servlet container or CommandBox.

## Install

=== "install-bx-module"
    ```bash
    install-bx-module bx-lens
    ```

=== "CommandBox"
    ```bash
    box install bx-lens
    ```

## Enable

Lens ships disabled. Turn it on in `boxlang.json`, under `modules.bxLens.settings`:

```json title="boxlang.json"
{
	"modules": {
		"bxLens": {
			"settings": {
				"enabled": true
			}
		}
	}
}
```

Restart the runtime. Every other setting has a default, so this is all you need. See [Configuration](configuration.md) for the rest.

## See the bar

Create a page and load it from the same machine.

```html title="index.bxm"
<bx:output>
	<h1>Hello Lens</h1>
</bx:output>
```

Open the page. A health strip appears at the bottom.

![The collapsed health strip](assets/screenshots/strip-collapsed.png)

Click the strip, a chip, or press ``Ctrl+` `` to open the panel.

![The opened panel on the Timeline tab](assets/screenshots/overview.png)

Add a message and a timer from code:

```javascript
lensMessage( "Page loaded", "info" );
users = lensMeasure( "load users", () => {
	return queryExecute( "SELECT 1 AS id" );
} );
```

See the [BIF reference](guides/bifs.md) for all functions.

## Try the demo app

The repository has a harness app that produces every kind of data Lens can show. See [Development](project/contributing.md#the-harness) and the [demo script](project/demo-script.md).

## Runtime notes

::: columns
::: column
### MiniServer
The harness and the end to end tests run on MiniServer.
:::
::: column
### CommandBox
Install the module the usual way and enable it in the server's `boxlang.json`.
:::
::: column
### Servlet containers
Lens uses the same request events as MiniServer. Only MiniServer is covered by the test suite, so test your setup and report problems.
:::
:::

## How the bar is delivered

Lens inlines its CSS, JavaScript and data into the HTML response. That adds about 110 KB to each HTML response and needs no extra network request. There is no separate asset endpoint.

## Requirements and limits

- Lens injects into HTML responses (`text/html` by default), before the last `</body>`. Other responses are recorded but show no bar. See [History](panels/history.md).
- Callers must pass the [access rules](security.md). By default only loopback and private network addresses see the bar.
- When a request ends in an uncaught exception or abort, core renders its own error page and Lens cannot inject into it. The request is still recorded in History. See [Exceptions](panels/exceptions.md).

## Next steps

- [Tour the panels](panels/index.md)
- [Learn the keyboard shortcuts](ui.md)
- [Read the security notes](security.md)
