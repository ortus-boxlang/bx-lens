# ⚡︎ BoxLang Module: BX Lens

```
|:------------------------------------------------------:|
| ⚡︎ B o x L a n g ⚡︎
| Dynamic : Modular : Productive
|:------------------------------------------------------:|
```

<blockquote>
	Copyright Since 2023 by Ortus Solutions, Corp
	<br>
	<a href="https://www.boxlang.io">www.boxlang.io</a> |
	<a href="https://www.ortussolutions.com">www.ortussolutions.com</a>
</blockquote>

<p>&nbsp;</p>

A request level debug bar for BoxLang web applications. Install the module, turn it on, and every HTML page gets a bar at the bottom with the request timeline, queries, templates, exceptions, HTTP calls, cache, modules, scopes and more. Everything is collected by Java code in memory. Nothing is written to disk.

![BX Lens on an N+1 page](docs/assets/screenshots/overview.png)

Lens works with the BoxLang MiniServer, CommandBox and servlet deployments, because it hooks the request lifecycle that web support already provides.

## Install

```bash
# OS binary
install-bx-module bx-lens

# CommandBox
box install bx-lens
```

Lens is **off by default** and only serves callers on loopback and private networks. Turn it on in `boxlang.json`:

```json
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

Never enable it on a public site. See [Security](docs/security.md).

## What you get

| Panel | Shows |
|---|---|
| **Issues** | Everything suspicious, ranked: exceptions, N+1 queries, slow queries, slow templates, failed HTTP calls |
| **Timeline** | One waterfall of templates, functions, queries, HTTP calls and transactions. Hover, click for detail, zoom, pan, filter, search, open the file in your editor |
| **Queries** | Every statement with its parameters, rows, time, datasource and the template line that ran it. Copy SQL |
| **Templates** | The include and call tree |
| **HTTP** | Outgoing HTTP calls with status, size and time |
| **Exceptions** | Caught and uncaught exceptions with BoxLang locations and the Java stack |
| **Messages and Timers** | What your code sends with `lensMessage()`, `lensDump()`, `lensMeasure()` |
| **Cache** | Every BoxCache cache with hit rate, objects, evictions and what this request did to it |
| **Modules** | Loaded modules with version, author, what they provide and activation time |
| **Request, Scopes, Runtime** | Headers, redacted scope snapshots, memory, GC, threads and versions |
| **History** | The last 50 requests, including JSON and SSE, recycled in memory |
| Your panels | Applications and other modules add panels with `lensPanel()` or the `onLensCollect` interception point |

A collapsed health strip turns amber or red when something is wrong and opens on Issues when an exception was caught. Resize it, detach it as a floating window, switch themes, and use the keyboard: <kbd>Ctrl</kbd>+<kbd>`</kbd> toggles, <kbd>1</kbd> to <kbd>9</kbd> switch tabs, <kbd>/</kbd> searches.

## Send things to the bar

```javascript
lensMessage( "Loaded #orders.len()# orders", "info" );
lensDump( order, "order after pricing" );

id = lensStart( "price calculation" );
// ...
lensStop( id );

orders = lensMeasure( "load orders", () => orderService.list() );

try {
	risky();
} catch ( any e ) {
	lensException( e );
}

lensPanel( "orm", "ORM" ).columns( [ "Entity", "ms" ] ).rows( rows ).badge( rows.len(), "none" );
```

## Documentation

The full documentation is built with [bx-sites](https://github.com/ortus-boxlang/bx-sites) from the `docs/` folder: configuration reference, every panel, the BIFs, extending Lens from your own module, security, the core event inventory, and troubleshooting.

```bash
bxSites serve
```

## Try it: the demo harness

`harness/` is a small shop app with a Derby in-memory database that produces every kind of data Lens can show: a healthy page, an N+1 loop, a slow query, caught and uncaught errors, outgoing HTTP, cache hits and misses, JSON and SSE endpoints, a form with a password, a session, transactions and custom panels.

```bash
./harness/start.sh          # builds the module and starts MiniServer on http://localhost:8085
DEV=1 SKIP_BUILD=1 ./harness/start.sh   # serve the UI files from src/main/bx/assets, edit and refresh
```

## Development

```bash
./gradlew downloadBoxLang          # BoxLang and web support jars, once
./gradlew shadowJar test           # build the module and run the Java tests
./gradlew spotlessApply            # Ortus Java formatting, run before every commit

cd e2e
npm ci
npx playwright install chromium
npm run e2e                        # starts the harness and drives every feature in a browser
npx playwright test screens.spec.ts   # regenerates docs/assets/screenshots
```

Set `LENS_CHROMIUM` to use a Chromium you already have. CI runs the Java tests on Linux and Windows and the Playwright suite on Linux, and uploads the report and the screenshots.

How it fits together:

- `src/main/java/.../LensService` owns the settings, the collectors, the in-memory history and the injector.
- `interceptors/collectors/*` are the Java collectors, one per panel. They run as BoxLang interceptors.
- `model/` holds the per request data, the issue engine and the snapshot that the UI reads.
- `src/main/bx/assets/` is the UI: Alpine.js, one CSS file and one template. The data travels as JSON inside the page.
- `src/main/bx/ModuleConfig.bx` declares every setting with its default.

Lens is request level and open source. BX Insights is the separate, licensed observability product.

## Ortus Sponsors

BoxLang is a professional open-source project and it is completely funded by the [community](https://patreon.com/ortussolutions) and [Ortus Solutions, Corp](https://www.ortussolutions.com). Ortus Patreons get many benefits like a cfcasts account, a FORGEBOX Pro account and so much more. If you are interested in becoming a sponsor, please visit our patronage page: [https://patreon.com/ortussolutions](https://patreon.com/ortussolutions)

### THE DAILY BREAD

> "I am the way, and the truth, and the life; no one comes to the Father, but by me (JESUS)" Jn 14:1-12
